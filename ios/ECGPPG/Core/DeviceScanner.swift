import Foundation

/// Perangkat BLE hasil pemindaian. `compatible` = mengiklankan layanan ECG/PPG yang dikenali aplikasi.
struct ScannedDevice: Identifiable, Equatable {
    let name: String?
    let address: String
    let rssi: Int
    let compatible: Bool

    var id: String { address }
    var displayName: String { name ?? "Perangkat tanpa nama" }

    /// 1..4 untuk indikator kekuatan sinyal.
    var signalLevel: Int {
        if rssi >= -55 { return 4 }
        if rssi >= -65 { return 3 }
        if rssi >= -75 { return 2 }
        return 1
    }
}

/// Pemindai perangkat. Sekarang `DummyDeviceScanner`; nanti diganti pemindai BLE nyata
/// (CBCentralManager + filter UUID layanan) yang memanggil callback yang sama.
@MainActor
protocol DeviceScanner: AnyObject {
    func start()
    func stop()
}

/// Meniru pemindaian BLE: perangkat muncul bertahap, RSSI berfluktuasi, berhenti otomatis setelah `durationMs`.
@MainActor
final class DummyDeviceScanner: DeviceScanner {
    private struct Fake { let name: String?; let address: String; let baseRssi: Int; let compatible: Bool; let appearAtMs: Int }

    private let fakes = [
        Fake(name: "ESP32_HEALTH_01", address: "C8:2B:96:3A:11:F0", baseRssi: -48, compatible: true, appearAtMs: 700),
        Fake(name: "Mi Smart Band 6", address: "F4:6A:DD:01:9B:22", baseRssi: -63, compatible: false, appearAtMs: 1_200),
        Fake(name: "ESP32_HEALTH_02", address: "C8:2B:96:3A:12:7C", baseRssi: -71, compatible: true, appearAtMs: 1_900),
        Fake(name: "JBL Flip 5", address: "78:44:05:9E:C3:10", baseRssi: -80, compatible: false, appearAtMs: 2_600),
        Fake(name: "Galaxy Watch4 (A1F2)", address: "A4:9B:4F:62:0D:E8", baseRssi: -67, compatible: false, appearAtMs: 3_300),
        Fake(name: nil, address: "5C:E5:0C:77:41:AB", baseRssi: -88, compatible: false, appearAtMs: 4_200),
    ]
    private let durationMs: Int
    private let onDevice: (ScannedDevice) -> Void
    private let onFinished: () -> Void
    private var task: Task<Void, Never>?

    init(durationMs: Int = 8_000, onDevice: @escaping (ScannedDevice) -> Void, onFinished: @escaping () -> Void) {
        self.durationMs = durationMs; self.onDevice = onDevice; self.onFinished = onFinished
    }

    func start() {
        let t0 = Date()
        task = Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                let elapsed = Int(Date().timeIntervalSince(t0) * 1000)
                for f in self.fakes where elapsed >= f.appearAtMs {
                    let rssi = min(-30, max(-100, f.baseRssi + Int.random(in: -3...3)))
                    self.onDevice(ScannedDevice(name: f.name, address: f.address, rssi: rssi, compatible: f.compatible))
                }
                if elapsed >= self.durationMs { self.onFinished(); return }
                try? await Task.sleep(nanoseconds: 500_000_000)
            }
        }
    }

    func stop() { task?.cancel(); task = nil }
}

/// Akun Google untuk demo. Login nyata (OAuth + Drive API) belum diimplementasikan.
struct DummyAccount: Hashable { let name: String; let email: String }

enum DummyAccounts {
    static let list = [
        DummyAccount(name: "Demo Pengguna", email: "demo.user@example.com"),
        DummyAccount(name: "Akun Lab ECG", email: "lab.ecg@example.com"),
    ]
}
