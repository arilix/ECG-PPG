package com.example.myapplication.dsp

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Deteksi R-peak streaming ala Pan-Tompkins sederhana (tanpa AI):
 * high-pass (kurangi baseline) -> low-pass -> turunan -> kuadrat -> integrasi jendela -> ambang adaptif.
 * Mengembalikan indeks sampel (puncak energi QRS; konsisten antar-detak, cukup untuk interval RR).
 */
class RPeakDetector(private val fs: Double) {
    private val win = max(1, (0.15 * fs).toInt())
    private val ring = DoubleArray(win)
    private var ringPos = 0
    private var ringSum = 0.0

    private var init = false
    private var base = 0.0
    private var smooth = 0.0
    private var prevSmooth = 0.0

    private var spki = 0.0
    private var npki = 0.0
    private var learnMax = 0.0
    private var n = 0L

    private var inPeak = false
    private var curMax = 0.0
    private var curIdx = 0L
    private var lastPeak = Long.MIN_VALUE / 2
    private val refractory = (0.25 * fs).toLong()
    /** Bila lebih dari 1 detik tanpa detak, level sinyal (SPKI) diturunkan agar detak yang lebih kecil tertangkap lagi. */
    private val decayAfter = (1.0 * fs).toLong()

    fun reset() {
        onGap()
        spki = 0.0; npki = 0.0; learnMax = 0.0; n = 0
        lastPeak = Long.MIN_VALUE / 2
    }

    /** Dipanggil saat ada sampel hilang: filter diinisialisasi ulang, level ambang dipertahankan. */
    fun onGap() {
        init = false
        ring.fill(0.0); ringSum = 0.0; ringPos = 0
        inPeak = false
    }

    fun process(x: Int, index: Long): Long? {
        if (!init) { base = x.toDouble(); smooth = 0.0; prevSmooth = 0.0; init = true }
        base += 0.01 * (x - base)
        val hp = x - base
        smooth += 0.3 * (hp - smooth)
        val d = smooth - prevSmooth
        prevSmooth = smooth
        val sq = d * d
        ringSum += sq - ring[ringPos]
        ring[ringPos] = sq
        ringPos = (ringPos + 1) % win
        val mwi = max(0.0, ringSum / win)
        n++

        if (n <= (2 * fs).toLong()) {          // fase belajar 2 detik pertama
            learnMax = max(learnMax, mwi)
            if (n == (2 * fs).toLong()) { spki = learnMax * 0.5; npki = learnMax * 0.05 }
            return null
        }
        if (!inPeak && index - lastPeak > decayAfter) spki *= 0.997
        val thr = npki + 0.25 * (spki - npki)
        var found: Long? = null
        if (!inPeak) {
            if (mwi > thr && index - lastPeak > refractory) {
                inPeak = true; curMax = mwi; curIdx = index
            } else if (mwi <= thr) {
                npki += 0.125 * (mwi - npki)
            }
        } else {
            if (mwi > curMax) { curMax = mwi; curIdx = index }
            else if (mwi < 0.5 * curMax) {
                spki += 0.125 * (curMax - spki)
                lastPeak = curIdx
                inPeak = false
                found = curIdx
            }
        }
        return found
    }
}

/**
 * Deteksi puncak denyut PPG (puncak sistolik) streaming: low-pass ~3,5 Hz, buang DC, puncak lokal.
 * - Ambang = 0,3 x rata-rata amplitudo 6 puncak terakhir (bukan envelope yang meluruh lambat).
 * - Refractory adaptif = 0,5 x median 5 interval terakhir, dibatasi 0,33-0,45 detik (menolak puncak dikrotik).
 * - Bila > 2 detik tanpa puncak, referensi amplitudo diturunkan agar denyut kecil tertangkap lagi.
 */
class PpgPeakDetector(private val fs: Double) {
    private var init = false
    private var smooth = 0.0
    private var base = 0.0
    private var env = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var lastPeak = -1e12
    private val amps = ArrayDeque<Double>()
    private val rrs = ArrayDeque<Double>()

    fun reset() { init = false; env = 0.0; lastPeak = -1e12; amps.clear(); rrs.clear() }
    fun onGap() { init = false }

    private fun medianRr(): Double {
        val l = rrs.sorted()
        return if (l.size % 2 == 1) l[l.size / 2] else (l[l.size / 2 - 1] + l[l.size / 2]) / 2
    }

    fun process(x: Int, index: Long): Long? {
        if (!init) { smooth = x.toDouble(); base = smooth; a1 = 0.0; a2 = 0.0; init = true }
        smooth += 0.2 * (x - smooth)
        base += 0.01 * (smooth - base)
        val ac = smooth - base
        env = max(env * 0.9995, ac)
        val cand = index - 1
        if (lastPeak > 0 && cand - lastPeak > 2 * fs && amps.isNotEmpty()) {
            for (i in amps.indices) amps[i] = amps[i] * 0.97
        }
        val ref = if (amps.isNotEmpty()) amps.average() else env
        val refractory = (if (rrs.size >= 2) minOf(0.45, maxOf(0.33, 0.5 * medianRr())) else 0.33) * fs
        var found: Long? = null
        if (a1 > a2 && a1 >= ac && a1 > 0.3 * ref && cand - lastPeak > refractory) {
            val gap = (cand - lastPeak) / fs
            if (lastPeak > 0 && gap < 2.0) { rrs.addLast(gap); if (rrs.size > 5) rrs.removeFirst() }
            amps.addLast(a1); if (amps.size > 6) amps.removeFirst()
            lastPeak = cand.toDouble()
            found = cand
        }
        a2 = a1; a1 = ac
        return found
    }
}

/** Mengubah waktu puncak (detik) menjadi BPM, interval RR, dan RMSSD (HRV sederhana). */
class RateTracker(private val minRr: Double = 0.30, private val maxRr: Double = 2.0) {
    private val rr = ArrayDeque<Double>()
    private var lastPeak: Double? = null

    fun reset() { rr.clear(); lastPeak = null }
    /** Rantai interval putus (mis. paket hilang): jangan hitung RR melintasi gap. */
    fun breakChain() { lastPeak = null }

    fun addPeak(t: Double) {
        val last = lastPeak
        if (last != null) {
            val d = t - last
            if (d < minRr) return                       // deteksi ganda, abaikan
            if (d <= maxRr) { rr.addLast(d); if (rr.size > 20) rr.removeFirst() } else rr.clear()
        }
        lastPeak = t
    }

    fun bpm(now: Double): Int? {
        val last = lastPeak ?: return null
        if (now - last > 3.0 || rr.size < 3) return null
        val recent = rr.toList().takeLast(8)
        return (60.0 / recent.average()).toInt()
    }

    fun lastRrMs(): Int? = rr.lastOrNull()?.let { (it * 1000).toInt() }

    /** RMSSD (ms) dari interval valid; null jika data kurang (< 6 interval). */
    fun rmssdMs(): Int? {
        if (rr.size < 6) return null
        val l = rr.toList()
        var s = 0.0
        for (i in 1 until l.size) { val d = (l[i] - l[i - 1]) * 1000; s += d * d }
        return sqrt(s / (l.size - 1)).toInt()
    }
}
