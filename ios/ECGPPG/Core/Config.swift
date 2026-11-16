import Foundation

/// Konstanta desain sesuai README (ECG 250 Hz, PPG 100 Hz).
enum Config {
    static let ecgFs = 250
    static let ppgFs = 100
    static let ecgPeriodUs: Int64 = 1_000_000 / Int64(ecgFs)   // 4000 us
    static let ppgPeriodUs: Int64 = 1_000_000 / Int64(ppgFs)   // 10000 us
    static let deviceId = "ESP32_HEALTH_01"
    /// Di bawah nilai ini IR dianggap "jari tidak menempel" (MAX30102 mentah).
    static let fingerMinIr = 20_000
    static let csvFormatVersion = 1
    static let csvHeader =
        "session_id,device_id,stream,sample_index,timestamp_us,ecg_adc,ppg_ir,ppg_red,lead_off,valid"
}

/// Deret (x = detik, y = nilai) dengan array kontigu.
/// - fixedCapacity > 0 : buffer geser untuk tampilan live (menyimpan separuh terbaru saat penuh).
/// - fixedCapacity = 0 : tumbuh otomatis (untuk replay CSV).
/// y = NaN berarti "putus" (gap / data tidak valid): garis tidak boleh disambung.
final class Series: @unchecked Sendable {
    private let fixedCapacity: Int
    private(set) var xs: [Double] = []
    private(set) var ys: [Float] = []

    init(fixedCapacity: Int = 0) {
        self.fixedCapacity = fixedCapacity
        let cap = fixedCapacity > 0 ? fixedCapacity : 4096
        xs.reserveCapacity(cap); ys.reserveCapacity(cap)
    }

    var size: Int { xs.count }

    func add(_ x: Double, _ y: Float) {
        if fixedCapacity > 0 && xs.count == fixedCapacity {
            let drop = xs.count - fixedCapacity / 2
            xs.removeFirst(drop); ys.removeFirst(drop)
        }
        xs.append(x); ys.append(y)
    }

    func clear() { xs.removeAll(keepingCapacity: true); ys.removeAll(keepingCapacity: true) }
    var firstX: Double { xs.first ?? 0 }
    var lastX: Double { xs.last ?? 0 }

    /// Indeks pertama dengan xs[i] >= x (size jika tidak ada).
    func lowerBound(_ x: Double) -> Int {
        var lo = 0, hi = xs.count
        while lo < hi {
            let mid = (lo + hi) >> 1
            if xs[mid] < x { lo = mid + 1 } else { hi = mid }
        }
        return lo
    }
}
