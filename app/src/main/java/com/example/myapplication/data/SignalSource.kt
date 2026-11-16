package com.example.myapplication.data

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.io.InputStream

/** Penerima paket data (dipanggil di main thread). Bentuknya meniru paket BLE batch dari ESP32. */
interface SignalListener {
    /** [startTimeUs] = timestamp ESP32 untuk sampel pertama di paket. */
    fun onEcg(startIndex: Long, startTimeUs: Long, adc: IntArray, leadOff: Boolean)
    fun onPpg(startIndex: Long, startTimeUs: Long, ir: IntArray, red: IntArray)
}

/**
 * Sumber data. Sekarang hanya [DummySignalSource]; nanti cukup buat
 * BleSignalSource yang memanggil listener yang sama saat notify BLE masuk.
 */
interface SignalSource {
    val name: String
    fun start(listener: SignalListener)
    fun stop()
}

object DummyData {
    private const val DIR = "dummy"

    fun parseEcg(input: InputStream): IntArray =
        input.bufferedReader().useLines { l -> l.filter { it.isNotBlank() }.map { it.trim().toInt() }.toList().toIntArray() }

    /** Baris "ir,red" -> (ir, red). */
    fun parsePpg(input: InputStream): Pair<IntArray, IntArray> {
        val ir = ArrayList<Int>()
        val red = ArrayList<Int>()
        input.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.forEach {
                val p = it.split(',')
                ir.add(p[0].trim().toInt()); red.add(p[1].trim().toInt())
            }
        }
        return ir.toIntArray() to red.toIntArray()
    }

    fun ecgRecords(ctx: android.content.Context): List<String> =
        (ctx.assets.list(DIR) ?: emptyArray()).filter { it.startsWith("ecg_") }.sorted()

    fun ppgRecords(ctx: android.content.Context): List<String> =
        (ctx.assets.list(DIR) ?: emptyArray()).filter { it.startsWith("ppg_") }.sorted()

    fun openAsset(ctx: android.content.Context, name: String): InputStream = ctx.assets.open("$DIR/$name")

    fun label(file: String): String = when (file) {
        "ecg_mitdb_100.txt" -> "MIT-BIH 100 (ritme normal)"
        "ecg_mitdb_119.txt" -> "MIT-BIH 119 (ada PVC)"
        "ecg_mitdb_208.txt" -> "MIT-BIH 208 (PVC/couplet)"
        else -> file.removeSuffix(".txt").replace('_', ' ').let {
            if (it.startsWith("ppg bidmc")) "BIDMC " + it.substringAfterLast(' ') else it
        }
    }
}

/**
 * Memutar ulang dataset (MIT-BIH ECG @250 Hz, BIDMC PPG @100 Hz) dengan kecepatan real-time,
 * dalam paket per 100 ms. Timestamp dihitung dari indeks sampel (seperti timestamp ESP32).
 */
class DummySignalSource(
    private val ecgData: IntArray,
    private val ppgIr: IntArray,
    private val ppgRed: IntArray,
    override val name: String,
) : SignalSource {
    /** Hilangkan paket ~200 ms tiap 15 detik, untuk menguji deteksi gap. */
    @Volatile var simulateDrops = false
    /** Meniru elektroda ECG terlepas (lead-off). */
    @Volatile var simulateLeadOff = false

    private val main = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    @Volatile private var running = false
    private var t0 = 0L
    private var ecgIdx = 0L
    private var ppgIdx = 0L
    private var nextDropUs = 15_000_000L
    private var dropTicks = 0

    override fun start(listener: SignalListener) {
        running = true
        t0 = System.nanoTime()
        val t = HandlerThread("dummy-source").also { it.start() }
        thread = t
        val h = Handler(t.looper)
        handler = h
        h.post(object : Runnable {
            override fun run() {
                if (!running) return
                tick(listener)
                h.postDelayed(this, TICK_MS)
            }
        })
    }

    override fun stop() {
        running = false
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        thread = null
        handler = null
    }

    private fun tick(listener: SignalListener) {
        val elapsedUs = (System.nanoTime() - t0) / 1000
        val ecgTarget = elapsedUs / Config.ECG_PERIOD_US
        val ppgTarget = elapsedUs / Config.PPG_PERIOD_US

        if (simulateDrops && elapsedUs >= nextDropUs) {
            dropTicks = 2
            nextDropUs += 15_000_000L
        }
        if (dropTicks > 0) {          // sampel "terbaca" oleh ESP32 tetapi paketnya hilang
            dropTicks--
            ecgIdx = ecgTarget
            ppgIdx = ppgTarget
            return
        }

        val leadOff = simulateLeadOff
        val nE = (ecgTarget - ecgIdx).toInt()
        if (nE > 0) {
            val a = IntArray(nE) { i ->
                if (leadOff) 4095 else ecgData[((ecgIdx + i) % ecgData.size).toInt()]
            }
            val start = ecgIdx
            ecgIdx += nE
            main.post { if (running) listener.onEcg(start, start * Config.ECG_PERIOD_US, a, leadOff) }
        }
        val nP = (ppgTarget - ppgIdx).toInt()
        if (nP > 0) {
            val ir = IntArray(nP) { i -> ppgIr[((ppgIdx + i) % ppgIr.size).toInt()] }
            val red = IntArray(nP) { i -> ppgRed[((ppgIdx + i) % ppgRed.size).toInt()] }
            val start = ppgIdx
            ppgIdx += nP
            main.post { if (running) listener.onPpg(start, start * Config.PPG_PERIOD_US, ir, red) }
        }
    }

    private companion object { const val TICK_MS = 100L }
}
