import Foundation
import SwiftUI

/// Pusat data live: menerima paket dari `SignalSource`, mengisi buffer grafik, menjalankan DSP,
/// dan (bila aktif) menulis CSV. Hidup di level aplikasi sehingga rekaman tetap jalan saat pindah tab.
@MainActor
final class MonitorEngine: ObservableObject, SignalListener {
    enum State { case disconnected, connecting, connected }

    let prefs = Prefs()
    let repo = SessionRepository()

    let ecgSeries = Series(fixedCapacity: 12_000)   // ~48 dtk, jendela maks 20 dtk
    let irSeries = Series(fixedCapacity: 4_800)
    let redSeries = Series(fixedCapacity: 4_800)

    private(set) var state = State.disconnected
    private(set) var leadOff = false
    private(set) var fingerPresent = false
    private(set) var sourceLabel = ""
    private(set) var recorder: SessionRecorder?
    /// Naik setiap ada pembaruan; dipakai grafik untuk menggambar ulang.
    private(set) var version = 0
    /// Pesan sekali tampil (error koneksi/penyimpanan, hasil penyalinan). Ditampilkan oleh ContentView.
    @Published var toast: String?
    private var toastTask: Task<Void, Never>?

    /// Perangkat yang sedang dihubungi/terhubung (nil = tidak ada).
    private(set) var device: ScannedDevice?
    /// Hasil pemindaian terakhir.
    private(set) var devices: [ScannedDevice] = []
    private(set) var scanning = false
    private var scanner: DeviceScanner?
    private var connectTask: Task<Void, Never>?

    private var source: SignalSource?

    private let ecgDet = RPeakDetector(fs: Double(Config.ecgFs))
    private let ecgRate = RateTracker()
    private let ppgDet = PpgPeakDetector(fs: Double(Config.ppgFs))
    private let ppgRate = RateTracker(minRr: 0.30, maxRr: 2.0)
    private var lastEcgT = 0.0
    private var lastPpgT = 0.0
    private var nextEcgIdx: Int64 = -1
    private var nextPpgIdx: Int64 = -1
    private var lastDeviceUs: Int64 = 0
    private(set) var ecgGapsLive = 0
    private(set) var ppgGapsLive = 0

    private func notifyUpdate() { version &+= 1; objectWillChange.send() }

    func showToast(_ message: String) {
        toast = message
        toastTask?.cancel()
        toastTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 3_500_000_000)
            if !Task.isCancelled { self?.toast = nil }
        }
    }

    func ecgBpm() -> Int? { ecgRate.bpm(now: lastEcgT) }
    func ppgBpm() -> Int? { ppgRate.bpm(now: lastPpgT) }
    func rrMs() -> Int? { ecgRate.lastRrMs() }
    func rmssdMs() -> Int? { ecgRate.rmssdMs() }

    // ---- pemindaian perangkat ------------------------------------------------------------

    func startScan() {
        guard !scanning, state == .disconnected else { return }
        devices.removeAll()
        scanning = true
        let s = DummyDeviceScanner(onDevice: { [weak self] d in
            guard let self else { return }
            if let i = self.devices.firstIndex(where: { $0.address == d.address }) { self.devices[i] = d } else { self.devices.append(d) }
            self.notifyUpdate()
        }, onFinished: { [weak self] in
            self?.scanning = false; self?.scanner = nil; self?.notifyUpdate()
        })
        scanner = s
        s.start()
        notifyUpdate()
    }

    func stopScan() {
        scanner?.stop(); scanner = nil
        if scanning { scanning = false; notifyUpdate() }
    }

    // ---- koneksi -------------------------------------------------------------------------

    func connect(_ target: ScannedDevice) {
        guard state == .disconnected else { return }
        stopScan()
        device = target
        state = .connecting; notifyUpdate()
        connectTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 1_500_000_000)   // meniru waktu connect + discover service BLE
            guard let self, !Task.isCancelled, self.state == .connecting else { return }
            if !target.compatible {       // meniru GATT: layanan ECG/PPG tidak ditemukan
                self.state = .disconnected; self.device = nil
                self.showToast("\(target.displayName) tidak memiliki layanan ECG/PPG yang dikenali.")
                self.notifyUpdate(); return
            }
            do {
                let src = try self.createSource()
                self.clearBuffers()
                self.source = src; self.sourceLabel = src.name
                self.state = .connected
                self.prefs.lastDeviceAddress = target.address
                src.start(self)
            } catch {
                self.state = .disconnected; self.device = nil
                self.showToast("Gagal memuat data dummy: \(error.localizedDescription)")
            }
            self.notifyUpdate()
        }
    }

    func disconnect() {
        connectTask?.cancel(); connectTask = nil
        _ = stopRecording(partial: true)
        source?.stop(); source = nil
        state = .disconnected
        device = nil
        leadOff = false; fingerPresent = false
        notifyUpdate()
    }

    private func createSource() throws -> DummySignalSource {
        let ecg = DummyData.parseEcg(try DummyData.read(prefs.ecgRecord))
        let ppg = DummyData.parsePpg(try DummyData.read(prefs.ppgRecord))
        let src = DummySignalSource(ecg: ecg, ir: ppg.ir, red: ppg.red,
                                    name: "simulasi: \(DummyData.label(prefs.ecgRecord)) + \(DummyData.label(prefs.ppgRecord))")
        applyFlags(src)
        return src
    }

    private func applyFlags(_ s: DummySignalSource) {
        s.simulateDrops = prefs.simulateDrops
        s.simulateLeadOff = prefs.simulateLeadOff
    }

    /// Dipanggil dari Pengaturan: berlaku langsung pada sumber yang sedang berjalan.
    func refreshSourceFlags() { if let s = source as? DummySignalSource { applyFlags(s) } }

    private func clearBuffers() {
        ecgSeries.clear(); irSeries.clear(); redSeries.clear()
        ecgDet.reset(); ecgRate.reset(); ppgDet.reset(); ppgRate.reset()
        lastEcgT = 0; lastPpgT = 0; nextEcgIdx = -1; nextPpgIdx = -1; lastDeviceUs = 0
        ecgGapsLive = 0; ppgGapsLive = 0
    }

    // ---- paket masuk ---------------------------------------------------------------------

    func onEcg(startIndex: Int64, startTimeUs: Int64, adc: [Int], leadOff: Bool) {
        guard state == .connected, !adc.isEmpty else { return }
        let period = Double(Config.ecgPeriodUs)
        if nextEcgIdx >= 0 && startIndex != nextEcgIdx {
            ecgGapsLive += 1
            ecgDet.onGap(); ecgRate.breakChain()
            ecgSeries.add(Double(startTimeUs) / 1e6 - period / 1e6 / 2, .nan)   // putus garis
        }
        nextEcgIdx = startIndex + Int64(adc.count)
        if leadOff && !self.leadOff { ecgDet.reset(); ecgRate.reset() }
        self.leadOff = leadOff
        for (i, v) in adc.enumerated() {
            let idx = startIndex + Int64(i)
            let t = (Double(startTimeUs) + Double(i) * period) / 1e6
            if leadOff { ecgSeries.add(t, .nan); continue }
            ecgSeries.add(t, Float(v))
            if let pk = ecgDet.process(v, idx) { ecgRate.addPeak(Double(pk) * period / 1e6) }
        }
        lastEcgT = (Double(startTimeUs) + Double(adc.count - 1) * period) / 1e6
        lastDeviceUs = max(lastDeviceUs, startTimeUs)
        record { try $0.writeEcg(startIndex: startIndex, startTimeUs: startTimeUs, adc: adc, leadOff: leadOff) }
        notifyUpdate()
    }

    func onPpg(startIndex: Int64, startTimeUs: Int64, ir: [Int], red: [Int]) {
        guard state == .connected, let lastIr = ir.last else { return }
        let period = Double(Config.ppgPeriodUs)
        if nextPpgIdx >= 0 && startIndex != nextPpgIdx {
            ppgGapsLive += 1
            ppgDet.onGap(); ppgRate.breakChain()
            let tg = Double(startTimeUs) / 1e6 - period / 1e6 / 2
            irSeries.add(tg, .nan); redSeries.add(tg, .nan)
        }
        nextPpgIdx = startIndex + Int64(ir.count)
        fingerPresent = lastIr >= Config.fingerMinIr
        for i in ir.indices {
            let t = (Double(startTimeUs) + Double(i) * period) / 1e6
            if ir[i] < Config.fingerMinIr {
                irSeries.add(t, .nan); redSeries.add(t, .nan); continue
            }
            irSeries.add(t, Float(ir[i])); redSeries.add(t, Float(red[i]))
            if let pk = ppgDet.process(ir[i], startIndex + Int64(i)) { ppgRate.addPeak(Double(pk) * period / 1e6) }
        }
        lastPpgT = (Double(startTimeUs) + Double(ir.count - 1) * period) / 1e6
        lastDeviceUs = max(lastDeviceUs, startTimeUs)
        record { try $0.writePpg(startIndex: startIndex, startTimeUs: startTimeUs, ir: ir, red: red) }
        notifyUpdate()
    }

    // ---- rekaman -------------------------------------------------------------------------

    private func record(_ block: (SessionRecorder) throws -> Void) {
        guard let r = recorder else { return }
        do { try block(r) } catch {
            showToast("Penyimpanan gagal (\(error.localizedDescription)); sesi disimpan parsial.")
            _ = stopRecording(partial: true)
        }
    }

    /// - Returns: pesan error, atau nil jika berhasil.
    func startRecording() -> String? {
        guard state == .connected else { return "Sambungkan perangkat dulu." }
        if recorder != nil { return nil }
        do {
            recorder = try SessionRecorder(dir: repo.dir, originUs: lastDeviceUs,
                                           deviceId: device?.displayName ?? Config.deviceId,
                                           deviceAddress: device?.address ?? "")
            notifyUpdate(); return nil
        } catch {
            return "Tidak bisa membuat file rekaman: \(error.localizedDescription)"
        }
    }

    @discardableResult
    func stopRecording(partial: Bool = false) -> SessionInfo? {
        guard let r = recorder else { return nil }
        recorder = nil
        let info = r.finish(partial: partial, sourceName: sourceLabel)
        exportAsync(info)
        notifyUpdate()
        return info
    }

    /// Salin CSV + metadata ke folder pilihan pengguna (jika ada), di thread terpisah.
    private func exportAsync(_ info: SessionInfo) {
        guard let bookmark = prefs.exportBookmark else { return }
        let label = StorageExporter.folderLabel(bookmark)
        repo.setSyncStatus(info, "exported", exportedTo: label)      // ditulis sebelum menyalin agar salinan konsisten
        let files = [info.csvFile, info.metaFile]
        Task.detached {
            let error: String? = {
                do { try StorageExporter.export(bookmark: bookmark, files: files); return nil }
                catch { return error.localizedDescription }
            }()
            await MainActor.run { [weak self] in
                guard let self else { return }
                if let error {
                    self.repo.setSyncStatus(info, "local_only")
                    self.showToast("Gagal menyalin ke folder pilihan: \(error)")
                } else {
                    self.showToast("Salinan sesi \(info.id) tersimpan di \(label)")
                }
            }
        }
    }
}
