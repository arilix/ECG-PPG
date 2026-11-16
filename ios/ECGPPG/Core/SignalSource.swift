import Foundation

/// Penerima paket data (dipanggil di main actor). Bentuknya meniru paket BLE batch dari ESP32.
@MainActor
protocol SignalListener: AnyObject {
    /// `startTimeUs` = timestamp ESP32 untuk sampel pertama di paket.
    func onEcg(startIndex: Int64, startTimeUs: Int64, adc: [Int], leadOff: Bool)
    func onPpg(startIndex: Int64, startTimeUs: Int64, ir: [Int], red: [Int])
}

/// Sumber data. Sekarang hanya `DummySignalSource`; nanti cukup buat
/// BleSignalSource (CoreBluetooth) yang memanggil listener yang sama saat notify BLE masuk.
@MainActor
protocol SignalSource: AnyObject {
    var name: String { get }
    func start(_ listener: SignalListener)
    func stop()
}

enum DummyData {
    enum LoadError: LocalizedError {
        case missing(String)
        var errorDescription: String? {
            switch self { case .missing(let n): return "aset \(n) tidak ditemukan" }
        }
    }

    private static func allFiles() -> [URL] {
        let flat = Bundle.main.urls(forResourcesWithExtension: "txt", subdirectory: nil) ?? []
        let sub = Bundle.main.urls(forResourcesWithExtension: "txt", subdirectory: "dummy") ?? []
        return flat + sub
    }

    static func ecgRecords() -> [String] { records(prefix: "ecg_") }
    static func ppgRecords() -> [String] { records(prefix: "ppg_") }

    private static func records(prefix: String) -> [String] {
        Array(Set(allFiles().map(\.lastPathComponent).filter { $0.hasPrefix(prefix) })).sorted()
    }

    static func read(_ name: String) throws -> String {
        guard let url = allFiles().first(where: { $0.lastPathComponent == name }) else { throw LoadError.missing(name) }
        return try String(contentsOf: url, encoding: .utf8)
    }

    static func parseEcg(_ text: String) -> [Int] {
        text.split(whereSeparator: \.isNewline).compactMap { Int($0.trimmingCharacters(in: .whitespaces)) }
    }

    /// Baris "ir,red" -> (ir, red).
    static func parsePpg(_ text: String) -> (ir: [Int], red: [Int]) {
        var ir: [Int] = [], red: [Int] = []
        for line in text.split(whereSeparator: \.isNewline) {
            let p = line.split(separator: ",")
            guard p.count >= 2,
                  let a = Int(p[0].trimmingCharacters(in: .whitespaces)),
                  let b = Int(p[1].trimmingCharacters(in: .whitespaces)) else { continue }
            ir.append(a); red.append(b)
        }
        return (ir, red)
    }

    static func label(_ file: String) -> String {
        switch file {
        case "ecg_mitdb_100.txt": return "MIT-BIH 100 (ritme normal)"
        case "ecg_mitdb_119.txt": return "MIT-BIH 119 (ada PVC)"
        case "ecg_mitdb_208.txt": return "MIT-BIH 208 (PVC/couplet)"
        default:
            let s = file.replacingOccurrences(of: ".txt", with: "").replacingOccurrences(of: "_", with: " ")
            if s.hasPrefix("ppg bidmc") { return "BIDMC " + (s.split(separator: " ").last.map(String.init) ?? "") }
            return s
        }
    }
}

/// Memutar ulang dataset (MIT-BIH ECG @250 Hz, BIDMC PPG @100 Hz) dengan kecepatan real-time,
/// dalam paket per 100 ms. Timestamp dihitung dari indeks sampel (seperti timestamp ESP32).
@MainActor
final class DummySignalSource: SignalSource {
    let name: String
    /// Hilangkan paket ~200 ms tiap 15 detik, untuk menguji deteksi gap.
    var simulateDrops = false
    /// Meniru elektroda ECG terlepas (lead-off).
    var simulateLeadOff = false

    private let ecgData: [Int]
    private let ppgIr: [Int]
    private let ppgRed: [Int]
    private var task: Task<Void, Never>?
    private var t0: UInt64 = 0
    private var ecgIdx: Int64 = 0
    private var ppgIdx: Int64 = 0
    private var nextDropUs: Int64 = 15_000_000
    private var dropTicks = 0

    private static let tickNs: UInt64 = 100_000_000

    init(ecg: [Int], ir: [Int], red: [Int], name: String) {
        ecgData = ecg; ppgIr = ir; ppgRed = red; self.name = name
    }

    func start(_ listener: SignalListener) {
        t0 = DispatchTime.now().uptimeNanoseconds
        task = Task { [weak self, weak listener] in
            while !Task.isCancelled {
                guard let self, let listener else { return }
                self.tick(listener)
                try? await Task.sleep(nanoseconds: Self.tickNs)
            }
        }
    }

    func stop() { task?.cancel(); task = nil }

    private func tick(_ listener: SignalListener) {
        let elapsedUs = Int64((DispatchTime.now().uptimeNanoseconds - t0) / 1000)
        let ecgTarget = elapsedUs / Config.ecgPeriodUs
        let ppgTarget = elapsedUs / Config.ppgPeriodUs

        if simulateDrops && elapsedUs >= nextDropUs {
            dropTicks = 2
            nextDropUs += 15_000_000
        }
        if dropTicks > 0 {          // sampel "terbaca" oleh ESP32 tetapi paketnya hilang
            dropTicks -= 1
            ecgIdx = ecgTarget
            ppgIdx = ppgTarget
            return
        }

        let leadOff = simulateLeadOff
        let nE = Int(ecgTarget - ecgIdx)
        if nE > 0 && !ecgData.isEmpty {
            let start = ecgIdx
            let a = (0..<nE).map { i in leadOff ? 4095 : ecgData[Int((start + Int64(i)) % Int64(ecgData.count))] }
            ecgIdx += Int64(nE)
            listener.onEcg(startIndex: start, startTimeUs: start * Config.ecgPeriodUs, adc: a, leadOff: leadOff)
        }
        let nP = Int(ppgTarget - ppgIdx)
        if nP > 0 && !ppgIr.isEmpty {
            let start = ppgIdx
            let ir = (0..<nP).map { i in ppgIr[Int((start + Int64(i)) % Int64(ppgIr.count))] }
            let red = (0..<nP).map { i in ppgRed[Int((start + Int64(i)) % Int64(ppgRed.count))] }
            ppgIdx += Int64(nP)
            listener.onPpg(startIndex: start, startTimeUs: start * Config.ppgPeriodUs, ir: ir, red: red)
        }
    }
}
