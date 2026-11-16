import Foundation

/// Kunci UserDefaults. Layar Pengaturan memakai @AppStorage dengan kunci yang sama.
enum PrefKey {
    static let ecgRecord = "ecg_record"
    static let ppgRecord = "ppg_record"
    static let windowSec = "window_sec"
    static let simDrops = "sim_drops"
    static let simLeadOff = "sim_leadoff"
    static let lastDevice = "last_device_addr"
    static let exportBookmark = "export_bookmark"
    static let googleName = "google_name"
    static let googleEmail = "google_email"
    /// 0 = ikuti sistem, 1 = terang, 2 = gelap.
    static let themeMode = "theme_mode"
}

final class Prefs {
    private let d = UserDefaults.standard

    var ecgRecord: String {
        get { d.string(forKey: PrefKey.ecgRecord) ?? "ecg_mitdb_100.txt" }
        set { d.set(newValue, forKey: PrefKey.ecgRecord) }
    }
    var ppgRecord: String {
        get { d.string(forKey: PrefKey.ppgRecord) ?? "ppg_bidmc_01.txt" }
        set { d.set(newValue, forKey: PrefKey.ppgRecord) }
    }
    var windowSec: Int {
        get { d.object(forKey: PrefKey.windowSec) as? Int ?? 10 }
        set { d.set(newValue, forKey: PrefKey.windowSec) }
    }
    var simulateDrops: Bool { d.bool(forKey: PrefKey.simDrops) }
    var simulateLeadOff: Bool { d.bool(forKey: PrefKey.simLeadOff) }
    var lastDeviceAddress: String? {
        get { d.string(forKey: PrefKey.lastDevice) }
        set { d.set(newValue, forKey: PrefKey.lastDevice) }
    }
    /// Bookmark folder tujuan salinan rekaman (dipilih lewat Files), nil = hanya penyimpanan internal.
    var exportBookmark: Data? { d.data(forKey: PrefKey.exportBookmark) }
}
