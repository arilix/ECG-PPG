import Foundation

/// Deteksi R-peak streaming ala Pan-Tompkins sederhana (tanpa AI):
/// high-pass (kurangi baseline) -> low-pass -> turunan -> kuadrat -> integrasi jendela -> ambang adaptif.
/// Mengembalikan indeks sampel (puncak energi QRS; konsisten antar-detak, cukup untuk interval RR).
final class RPeakDetector {
    private let fs: Double
    private let win: Int
    private var ring: [Double]
    private var ringPos = 0
    private var ringSum = 0.0

    private var initialized = false
    private var base = 0.0
    private var smooth = 0.0
    private var prevSmooth = 0.0

    private var spki = 0.0
    private var npki = 0.0
    private var learnMax = 0.0
    private var n: Int64 = 0

    private var inPeak = false
    private var curMax = 0.0
    private var curIdx: Int64 = 0
    private var lastPeak: Int64 = Int64.min / 2
    private let refractory: Int64
    /// Bila lebih dari 1 detik tanpa detak, level sinyal (SPKI) diturunkan agar detak yang lebih kecil tertangkap lagi.
    private let decayAfter: Int64

    init(fs: Double) {
        self.fs = fs
        win = max(1, Int(0.15 * fs))
        ring = Array(repeating: 0, count: win)
        refractory = Int64(0.25 * fs)
        decayAfter = Int64(1.0 * fs)
    }

    func reset() {
        onGap()
        spki = 0; npki = 0; learnMax = 0; n = 0
        lastPeak = Int64.min / 2
    }

    /// Dipanggil saat ada sampel hilang: filter diinisialisasi ulang, level ambang dipertahankan.
    func onGap() {
        initialized = false
        for i in ring.indices { ring[i] = 0 }
        ringSum = 0; ringPos = 0
        inPeak = false
    }

    func process(_ x: Int, _ index: Int64) -> Int64? {
        let xd = Double(x)
        if !initialized { base = xd; smooth = 0; prevSmooth = 0; initialized = true }
        base += 0.01 * (xd - base)
        let hp = xd - base
        smooth += 0.3 * (hp - smooth)
        let d = smooth - prevSmooth
        prevSmooth = smooth
        let sq = d * d
        ringSum += sq - ring[ringPos]
        ring[ringPos] = sq
        ringPos = (ringPos + 1) % win
        let mwi = max(0.0, ringSum / Double(win))
        n += 1

        let learn = Int64(2 * fs)
        if n <= learn {                       // fase belajar 2 detik pertama
            learnMax = max(learnMax, mwi)
            if n == learn { spki = learnMax * 0.5; npki = learnMax * 0.05 }
            return nil
        }
        if !inPeak && index - lastPeak > decayAfter { spki *= 0.997 }
        let thr = npki + 0.25 * (spki - npki)
        var found: Int64?
        if !inPeak {
            if mwi > thr && index - lastPeak > refractory {
                inPeak = true; curMax = mwi; curIdx = index
            } else if mwi <= thr {
                npki += 0.125 * (mwi - npki)
            }
        } else {
            if mwi > curMax { curMax = mwi; curIdx = index }
            else if mwi < 0.5 * curMax {
                spki += 0.125 * (curMax - spki)
                lastPeak = curIdx
                inPeak = false
                found = curIdx
            }
        }
        return found
    }
}

/// Deteksi puncak denyut PPG (puncak sistolik) streaming: low-pass ~3,5 Hz, buang DC, puncak lokal.
/// - Ambang = 0,3 x rata-rata amplitudo 6 puncak terakhir.
/// - Refractory adaptif = 0,5 x median 5 interval terakhir, dibatasi 0,33-0,45 detik (menolak puncak dikrotik).
/// - Bila > 2 detik tanpa puncak, referensi amplitudo diturunkan agar denyut kecil tertangkap lagi.
final class PpgPeakDetector {
    private let fs: Double
    private var initialized = false
    private var smooth = 0.0
    private var base = 0.0
    private var env = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var lastPeak = -1e12
    private var amps: [Double] = []
    private var rrs: [Double] = []

    init(fs: Double) { self.fs = fs }

    func reset() { initialized = false; env = 0; lastPeak = -1e12; amps.removeAll(); rrs.removeAll() }
    func onGap() { initialized = false }

    private func medianRr() -> Double {
        let l = rrs.sorted()
        return l.count % 2 == 1 ? l[l.count / 2] : (l[l.count / 2 - 1] + l[l.count / 2]) / 2
    }

    func process(_ x: Int, _ index: Int64) -> Int64? {
        let xd = Double(x)
        if !initialized { smooth = xd; base = smooth; a1 = 0; a2 = 0; initialized = true }
        smooth += 0.2 * (xd - smooth)
        base += 0.01 * (smooth - base)
        let ac = smooth - base
        env = max(env * 0.9995, ac)
        let cand = index - 1
        let since = Double(cand) - lastPeak
        if lastPeak > 0 && since > 2 * fs && !amps.isEmpty {
            for i in amps.indices { amps[i] *= 0.97 }
        }
        let ref = amps.isEmpty ? env : amps.reduce(0, +) / Double(amps.count)
        let refractory = (rrs.count >= 2 ? min(0.45, max(0.33, 0.5 * medianRr())) : 0.33) * fs
        var found: Int64?
        if a1 > a2 && a1 >= ac && a1 > 0.3 * ref && since > refractory {
            let gap = since / fs
            if lastPeak > 0 && gap < 2.0 { rrs.append(gap); if rrs.count > 5 { rrs.removeFirst() } }
            amps.append(a1); if amps.count > 6 { amps.removeFirst() }
            lastPeak = Double(cand)
            found = cand
        }
        a2 = a1; a1 = ac
        return found
    }
}

/// Mengubah waktu puncak (detik) menjadi BPM, interval RR, dan RMSSD (HRV sederhana).
final class RateTracker {
    private let minRr: Double
    private let maxRr: Double
    private var rr: [Double] = []
    private var lastPeak: Double?

    init(minRr: Double = 0.30, maxRr: Double = 2.0) { self.minRr = minRr; self.maxRr = maxRr }

    func reset() { rr.removeAll(); lastPeak = nil }
    /// Rantai interval putus (mis. paket hilang): jangan hitung RR melintasi gap.
    func breakChain() { lastPeak = nil }

    func addPeak(_ t: Double) {
        if let last = lastPeak {
            let d = t - last
            if d < minRr { return }                       // deteksi ganda, abaikan
            if d <= maxRr { rr.append(d); if rr.count > 20 { rr.removeFirst() } } else { rr.removeAll() }
        }
        lastPeak = t
    }

    func bpm(now: Double) -> Int? {
        guard let last = lastPeak, now - last <= 3.0, rr.count >= 3 else { return nil }
        let recent = rr.suffix(8)
        return Int(60.0 / (recent.reduce(0, +) / Double(recent.count)))
    }

    func lastRrMs() -> Int? { rr.last.map { Int($0 * 1000) } }

    /// RMSSD (ms) dari interval valid; nil jika data kurang (< 6 interval).
    func rmssdMs() -> Int? {
        guard rr.count >= 6 else { return nil }
        var s = 0.0
        for i in 1..<rr.count { let d = (rr[i] - rr[i - 1]) * 1000; s += d * d }
        return Int((s / Double(rr.count - 1)).squareRoot())
    }
}
