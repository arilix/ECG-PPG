package com.example.myapplication.data

import android.os.Handler
import android.os.Looper
import kotlin.random.Random

/** Perangkat BLE hasil pemindaian. [compatible] = mengiklankan layanan ECG/PPG yang dikenali aplikasi. */
data class ScannedDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val compatible: Boolean,
) {
    val displayName: String get() = name ?: "Perangkat tanpa nama"

    /** 1..4 untuk indikator kekuatan sinyal. */
    val signalLevel: Int get() = when {
        rssi >= -55 -> 4
        rssi >= -65 -> 3
        rssi >= -75 -> 2
        else -> 1
    }
}

/**
 * Pemindai perangkat. Sekarang [DummyDeviceScanner]; nanti diganti pemindai BLE nyata
 * (BluetoothLeScanner + filter UUID layanan) yang memanggil [Callback] yang sama.
 */
interface DeviceScanner {
    interface Callback {
        /** Dipanggil (main thread) saat perangkat baru ditemukan atau RSSI-nya berubah. */
        fun onDevice(device: ScannedDevice)
        fun onFinished()
    }

    fun start()
    fun stop()
}

/** Meniru pemindaian BLE: perangkat muncul bertahap, RSSI berfluktuasi, berhenti otomatis setelah [durationMs]. */
class DummyDeviceScanner(
    private val callback: DeviceScanner.Callback,
    private val durationMs: Long = 8_000,
) : DeviceScanner {
    private class Fake(val name: String?, val address: String, val baseRssi: Int, val compatible: Boolean, val appearAtMs: Long)

    private val fakes = listOf(
        Fake("ESP32_HEALTH_01", "C8:2B:96:3A:11:F0", -48, true, 700),
        Fake("Mi Smart Band 6", "F4:6A:DD:01:9B:22", -63, false, 1_200),
        Fake("ESP32_HEALTH_02", "C8:2B:96:3A:12:7C", -71, true, 1_900),
        Fake("JBL Flip 5", "78:44:05:9E:C3:10", -80, false, 2_600),
        Fake("Galaxy Watch4 (A1F2)", "A4:9B:4F:62:0D:E8", -67, false, 3_300),
        Fake(null, "5C:E5:0C:77:41:AB", -88, false, 4_200),
    )
    private val main = Handler(Looper.getMainLooper())
    private val rnd = Random(System.nanoTime())
    private var t0 = 0L
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val elapsed = System.currentTimeMillis() - t0
            for (f in fakes) {
                if (elapsed < f.appearAtMs) continue
                val rssi = (f.baseRssi + rnd.nextInt(-3, 4)).coerceIn(-100, -30)
                callback.onDevice(ScannedDevice(f.name, f.address, rssi, f.compatible))
            }
            if (elapsed >= durationMs) { running = false; callback.onFinished(); return }
            main.postDelayed(this, 500)
        }
    }

    override fun start() { running = true; t0 = System.currentTimeMillis(); main.post(tick) }
    override fun stop() { running = false; main.removeCallbacks(tick) }
}
