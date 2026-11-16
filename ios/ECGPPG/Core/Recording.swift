import Foundation

/// Metadata satu sesi (dibaca dari `<session_id>.metadata.json`).
struct SessionInfo: Identifiable, Hashable {
    let id: String
    let csvFile: URL
    let metaFile: URL
    let startedAtMs: Int64
    let durationS: Double       // < 0 = tidak diketahui (sesi tanpa metadata / crash)
    let ecgSamples: Int64
    let ppgSamples: Int64
    let ecgLost: Int64
    let ppgLost: Int64
    let partial: Bool
    let syncStatus: String
    let source: String
}

/// Menulis satu sesi ke satu CSV format long/sparse, secara bertahap.
final class SessionRecorder {
    let sessionId: String = String(UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased().prefix(6))
    let startedAtMs = Int64(Date().timeIntervalSince1970 * 1000)
    let csvFile: URL
    private let metaFile: URL
    private let handle: FileHandle
    private let originUs: Int64
    private let deviceId: String
    private let deviceAddress: String

    private(set) var ecgRows: Int64 = 0
    private(set) var ppgRows: Int64 = 0
    private(set) var ecgLost: Int64 = 0
    private(set) var ppgLost: Int64 = 0
    private var nextEcg: Int64 = -1
    private var nextPpg: Int64 = -1
    private var lastUs: Int64 = 0
    private var batches = 0
    private var buffer = ""

    var durationS: Double { Double(lastUs) / 1e6 }

    init(dir: URL, originUs: Int64, deviceId: String, deviceAddress: String = "") throws {
        self.originUs = originUs; self.deviceId = deviceId; self.deviceAddress = deviceAddress
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd_HHmmss"
        let stamp = f.string(from: Date(timeIntervalSince1970: Double(startedAtMs) / 1000))
        csvFile = dir.appendingPathComponent("ECGPPG_\(stamp)_\(sessionId).csv")
        metaFile = dir.appendingPathComponent("\(sessionId).metadata.json")
        guard FileManager.default.createFile(atPath: csvFile.path, contents: Data((Config.csvHeader + "\n").utf8)) else {
            throw CocoaError(.fileWriteUnknown)
        }
        handle = try FileHandle(forWritingTo: csvFile)
        try handle.seekToEnd()
    }

    func writeEcg(startIndex: Int64, startTimeUs: Int64, adc: [Int], leadOff: Bool) throws {
        if nextEcg >= 0 && startIndex > nextEcg { ecgLost += startIndex - nextEcg }
        nextEcg = startIndex + Int64(adc.count)
        let lo = leadOff ? 1 : 0
        for (i, v) in adc.enumerated() {
            let ts = startTimeUs + Int64(i) * Config.ecgPeriodUs - originUs
            buffer += "\(sessionId),\(deviceId),ecg,\(startIndex + Int64(i)),\(ts),\(v),,,\(lo),\(1 - lo)\n"
            lastUs = max(lastUs, ts)
        }
        ecgRows += Int64(adc.count)
        try flushSometimes()
    }

    func writePpg(startIndex: Int64, startTimeUs: Int64, ir: [Int], red: [Int]) throws {
        if nextPpg >= 0 && startIndex > nextPpg { ppgLost += startIndex - nextPpg }
        nextPpg = startIndex + Int64(ir.count)
        for i in ir.indices {
            let ts = startTimeUs + Int64(i) * Config.ppgPeriodUs - originUs
            let valid = ir[i] >= Config.fingerMinIr ? 1 : 0
            buffer += "\(sessionId),\(deviceId),ppg,\(startIndex + Int64(i)),\(ts),,\(ir[i]),\(red[i]),,\(valid)\n"
            lastUs = max(lastUs, ts)
        }
        ppgRows += Int64(ir.count)
        try flushSometimes()
    }

    private func flushSometimes() throws {
        batches += 1
        if batches % 10 == 0 { try flush() }
    }

    private func flush() throws {
        guard !buffer.isEmpty else { return }
        try handle.write(contentsOf: Data(buffer.utf8))
        buffer = ""
    }

    /// Menutup file dan menulis metadata pendamping.
    func finish(partial: Bool, sourceName: String) -> SessionInfo {
        try? flush()
        try? handle.close()
        let utc = ISO8601DateFormatter()
        let json: [String: Any] = [
            "format_version": Config.csvFormatVersion,
            "session_id": sessionId,
            "device_id": deviceId,
            "device_address": deviceAddress,
            "csv_file": csvFile.lastPathComponent,
            "started_utc": utc.string(from: Date(timeIntervalSince1970: Double(startedAtMs) / 1000)),
            "started_epoch_ms": startedAtMs,
            "timezone": TimeZone.current.identifier,
            "duration_s": durationS,
            "target_ecg_hz": Config.ecgFs,
            "target_ppg_hz": Config.ppgFs,
            "ecg_samples": ecgRows,
            "ppg_samples": ppgRows,
            "ecg_lost": ecgLost,
            "ppg_lost": ppgLost,
            "partial": partial,
            "sync_status": "local_only",
            "source": sourceName,
        ]
        if let data = try? JSONSerialization.data(withJSONObject: json, options: [.prettyPrinted, .sortedKeys]) {
            try? data.write(to: metaFile)
        }
        return SessionRepository.parseMeta(metaFile, csv: csvFile) ?? SessionInfo(
            id: sessionId, csvFile: csvFile, metaFile: metaFile, startedAtMs: startedAtMs, durationS: durationS,
            ecgSamples: ecgRows, ppgSamples: ppgRows, ecgLost: ecgLost, ppgLost: ppgLost,
            partial: partial, syncStatus: "local_only", source: sourceName)
    }
}

/// Daftar/hapus sesi di penyimpanan lokal aplikasi (`Documents/recordings`, terlihat juga di app Files).
final class SessionRepository {
    let dir: URL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("recordings", isDirectory: true)

    func list() -> [SessionInfo] {
        guard let files = try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.contentModificationDateKey]) else { return [] }
        var result: [SessionInfo] = []
        var withMeta = Set<String>()
        for m in files where m.lastPathComponent.hasSuffix(".metadata.json") {
            guard let j = Self.readJson(m), let csvName = j["csv_file"] as? String else { continue }
            withMeta.insert(csvName)
            if let s = Self.parseMeta(m, csv: dir.appendingPathComponent(csvName)) { result.append(s) }
        }
        // CSV tanpa metadata (mis. aplikasi mati saat merekam) tetap ditampilkan sebagai parsial.
        for c in files where c.pathExtension == "csv" && !withMeta.contains(c.lastPathComponent) {
            let id = String(c.deletingPathExtension().lastPathComponent.split(separator: "_").last ?? "?")
            let mod = (try? c.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? Date()
            result.append(SessionInfo(id: id, csvFile: c, metaFile: dir.appendingPathComponent("\(id).metadata.json"),
                                      startedAtMs: Int64(mod.timeIntervalSince1970 * 1000), durationS: -1,
                                      ecgSamples: 0, ppgSamples: 0, ecgLost: 0, ppgLost: 0,
                                      partial: true, syncStatus: "local_only", source: "?"))
        }
        return result.sorted { $0.startedAtMs > $1.startedAtMs }
    }

    func find(_ id: String) -> SessionInfo? { list().first { $0.id == id } }

    func delete(_ s: SessionInfo) {
        try? FileManager.default.removeItem(at: s.csvFile)
        try? FileManager.default.removeItem(at: s.metaFile)
    }

    /// Memperbarui `sync_status` (mis. "exported") dan tujuan salinan di berkas metadata.
    func setSyncStatus(_ s: SessionInfo, _ status: String, exportedTo: String? = nil) {
        guard var j = Self.readJson(s.metaFile) else { return }
        j["sync_status"] = status
        j["exported_to"] = exportedTo
        if let data = try? JSONSerialization.data(withJSONObject: j, options: [.prettyPrinted, .sortedKeys]) {
            try? data.write(to: s.metaFile)
        }
    }

    static func readJson(_ url: URL) -> [String: Any]? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
    }

    static func parseMeta(_ meta: URL, csv: URL) -> SessionInfo? {
        guard let j = readJson(meta),
              let id = j["session_id"] as? String,
              let started = (j["started_epoch_ms"] as? NSNumber)?.int64Value,
              let dur = (j["duration_s"] as? NSNumber)?.doubleValue else { return nil }
        func i64(_ k: String) -> Int64 { (j[k] as? NSNumber)?.int64Value ?? 0 }
        return SessionInfo(
            id: id, csvFile: csv, metaFile: meta, startedAtMs: started, durationS: dur,
            ecgSamples: i64("ecg_samples"), ppgSamples: i64("ppg_samples"),
            ecgLost: i64("ecg_lost"), ppgLost: i64("ppg_lost"),
            partial: (j["partial"] as? Bool) ?? false,
            syncStatus: (j["sync_status"] as? String) ?? "local_only",
            source: (j["source"] as? String) ?? "")
    }
}

enum CsvFormatError: LocalizedError {
    case empty, badHeader, noData
    var errorDescription: String? {
        switch self {
        case .empty: return "File kosong"
        case .badHeader: return "Header CSV tidak dikenali"
        case .noData: return "Tidak ada data sampel valid"
        }
    }
}

struct LoadedSession: @unchecked Sendable {
    let ecg: Series, ir: Series, red: Series
    let ecgGaps: Int, ppgGaps: Int, badRows: Int
    let avgEcgBpm: Int?
}

/// Membaca CSV sesi dan menyusunnya kembali menjadi deret untuk line chart (gap/invalid -> putus garis).
enum CsvLoader {
    static func load(_ file: URL) throws -> LoadedSession {
        let text = String(decoding: try Data(contentsOf: file), as: UTF8.self)
        var lines = text.split(separator: "\n", omittingEmptySubsequences: true).makeIterator()
        guard let header = lines.next() else { throw CsvFormatError.empty }
        guard header.trimmingCharacters(in: .whitespacesAndNewlines) == Config.csvHeader else { throw CsvFormatError.badHeader }

        let ecg = Series(), ir = Series(), red = Series()
        var ecgGaps = 0, ppgGaps = 0, bad = 0
        var lastEcgT = Int64.min, lastPpgT = Int64.min
        while let raw = lines.next() {
            let line = raw.hasSuffix("\r") ? raw.dropLast() : raw
            let p = line.split(separator: ",", omittingEmptySubsequences: false)
            guard p.count >= 10, let ts = Int64(p[4]) else { bad += 1; continue }
            let valid = p[9] == "1"
            let x = Double(ts) / 1e6
            switch p[2] {
            case "ecg":
                if lastEcgT != Int64.min && Double(ts - lastEcgT) > 2.5 * Double(Config.ecgPeriodUs) {
                    ecgGaps += 1; ecg.add(Double(lastEcgT) / 1e6 + 0.001, .nan)
                }
                ecg.add(x, valid ? (Float(p[5]) ?? .nan) : .nan)
                lastEcgT = ts
            case "ppg":
                if lastPpgT != Int64.min && Double(ts - lastPpgT) > 2.5 * Double(Config.ppgPeriodUs) {
                    ppgGaps += 1
                    ir.add(Double(lastPpgT) / 1e6 + 0.001, .nan); red.add(Double(lastPpgT) / 1e6 + 0.001, .nan)
                }
                ir.add(x, valid ? (Float(p[6]) ?? .nan) : .nan)
                red.add(x, valid ? (Float(p[7]) ?? .nan) : .nan)
                lastPpgT = ts
            default:
                bad += 1
            }
        }
        if ecg.size == 0 && ir.size == 0 { throw CsvFormatError.noData }
        return LoadedSession(ecg: ecg, ir: ir, red: red, ecgGaps: ecgGaps, ppgGaps: ppgGaps, badRows: bad,
                             avgEcgBpm: averageBpm(ecg))
    }

    /// BPM rata-rata ECG dari DSP yang sama dengan live (detektor di-reset di tiap gap).
    private static func averageBpm(_ ecg: Series) -> Int? {
        guard ecg.size >= Config.ecgFs * 10 else { return nil }
        let det = RPeakDetector(fs: Double(Config.ecgFs))
        let rate = RateTracker()
        var peaks = 0
        var first = Double.nan, last = Double.nan
        for i in 0..<ecg.size {
            let y = ecg.ys[i]
            if y.isNaN { det.onGap(); rate.breakChain(); continue }
            guard let pk = det.process(Int(y.rounded()), Int64(i)) else { continue }
            let t = ecg.xs[min(Int(pk), ecg.size - 1)]
            rate.addPeak(t); peaks += 1
            if first.isNaN { first = t }
            last = t
        }
        guard peaks >= 5, last > first else { return nil }
        return Int(Double(peaks - 1) * 60.0 / (last - first))
    }
}

/// Menyalin berkas rekaman ke folder yang dipilih pengguna lewat app Files (security-scoped bookmark).
/// Rekaman utama tetap di penyimpanan internal aplikasi; ini hanya salinan.
enum StorageExporter {
    static func resolve(_ bookmark: Data) throws -> URL {
        var stale = false
        return try URL(resolvingBookmarkData: bookmark, bookmarkDataIsStale: &stale)
    }

    static func export(bookmark: Data, files: [URL]) throws {
        let folder = try resolve(bookmark)
        let ok = folder.startAccessingSecurityScopedResource()
        defer { if ok { folder.stopAccessingSecurityScopedResource() } }
        let fm = FileManager.default
        for f in files where fm.fileExists(atPath: f.path) {
            var dest = folder.appendingPathComponent(f.lastPathComponent)
            var n = 1
            while fm.fileExists(atPath: dest.path) {
                let base = f.deletingPathExtension().lastPathComponent
                dest = folder.appendingPathComponent("\(base) (\(n)).\(f.pathExtension)"); n += 1
            }
            try fm.copyItem(at: f, to: dest)
        }
    }

    /// Teks ramah-pengguna untuk folder pilihan, mis. "ECG".
    static func folderLabel(_ bookmark: Data) -> String {
        (try? resolve(bookmark).lastPathComponent) ?? "Folder terpilih"
    }
}
