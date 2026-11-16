package com.example.myapplication

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.example.myapplication.data.Config
import com.example.myapplication.data.DeviceScanner
import com.example.myapplication.data.DummyData
import com.example.myapplication.data.DummyDeviceScanner
import com.example.myapplication.data.ScannedDevice
import com.example.myapplication.data.StorageExporter
import android.net.Uri
import com.example.myapplication.data.DummySignalSource
import com.example.myapplication.data.Prefs
import com.example.myapplication.data.Series
import com.example.myapplication.data.SessionInfo
import com.example.myapplication.data.SessionRecorder
import com.example.myapplication.data.SessionRepository
import com.example.myapplication.data.SignalListener
import com.example.myapplication.data.SignalSource
import com.example.myapplication.dsp.PpgPeakDetector
import com.example.myapplication.dsp.RPeakDetector
import com.example.myapplication.dsp.RateTracker
import java.io.IOException

/**
 * Pusat data live: menerima paket dari [SignalSource], mengisi buffer grafik, menjalankan DSP,
 * dan (bila aktif) menulis CSV. Hidup di level aplikasi sehingga rekaman tetap jalan saat pindah tab.
 * Semua dipanggil di main thread.
 */
class MonitorEngine(private val app: Application) : SignalListener {
    interface Listener { fun onEngineUpdate() }
    enum class State { DISCONNECTED, CONNECTING, CONNECTED }

    val prefs = Prefs(app)
    val repo = SessionRepository(app)

    val ecgSeries = Series(12_000)   // ~48 dtk, jendela maks 20 dtk
    val irSeries = Series(4_800)
    val redSeries = Series(4_800)

    var state = State.DISCONNECTED; private set
    var leadOff = false; private set
    var fingerPresent = false; private set
    var sourceLabel = ""; private set
    var recorder: SessionRecorder? = null; private set
    var recordError: String? = null
    /** Pesan informasi sekali tampil (mis. hasil penyalinan ke folder). Ditampilkan oleh MainActivity. */
    var notice: String? = null
    private val ui = Handler(Looper.getMainLooper())

    /** Perangkat yang sedang dihubungi/terhubung (null = tidak ada). */
    var device: ScannedDevice? = null; private set
    /** Hasil pemindaian terakhir. */
    val devices = ArrayList<ScannedDevice>()
    var scanning = false; private set
    private var scanner: DeviceScanner? = null

    private var source: SignalSource? = null
    private val main = Handler(Looper.getMainLooper())
    private val listeners = ArrayList<Listener>()

    private val ecgDet = RPeakDetector(Config.ECG_FS.toDouble())
    private val ecgRate = RateTracker()
    private val ppgDet = PpgPeakDetector(Config.PPG_FS.toDouble())
    private val ppgRate = RateTracker(minRr = 0.30, maxRr = 2.0)
    private var lastEcgT = 0.0
    private var lastPpgT = 0.0
    private var nextEcgIdx = -1L
    private var nextPpgIdx = -1L
    private var lastDeviceUs = 0L
    var ecgGapsLive = 0; private set
    var ppgGapsLive = 0; private set

    fun addListener(l: Listener) { if (l !in listeners) listeners.add(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }
    private fun notifyUpdate() { listeners.toList().forEach { it.onEngineUpdate() } }

    fun ecgBpm() = ecgRate.bpm(lastEcgT)
    fun ppgBpm() = ppgRate.bpm(lastPpgT)
    fun rrMs() = ecgRate.lastRrMs()
    fun rmssdMs() = ecgRate.rmssdMs()

    // ---- koneksi -------------------------------------------------------------------------

    // ---- pemindaian perangkat ------------------------------------------------------------

    fun startScan() {
        if (scanning || state != State.DISCONNECTED) return
        devices.clear()
        scanning = true
        scanner = DummyDeviceScanner(object : DeviceScanner.Callback {
            override fun onDevice(device: ScannedDevice) {
                val i = devices.indexOfFirst { it.address == device.address }
                if (i >= 0) devices[i] = device else devices.add(device)
                notifyUpdate()
            }
            override fun onFinished() { scanning = false; scanner = null; notifyUpdate() }
        }).also { it.start() }
        notifyUpdate()
    }

    fun stopScan() {
        scanner?.stop(); scanner = null
        if (scanning) { scanning = false; notifyUpdate() }
    }

    // ---- koneksi -------------------------------------------------------------------------

    fun connect(target: ScannedDevice) {
        if (state != State.DISCONNECTED) return
        stopScan()
        device = target
        state = State.CONNECTING; notifyUpdate()
        main.postDelayed({
            if (state != State.CONNECTING) return@postDelayed
            if (!target.compatible) {       // meniru GATT: layanan ECG/PPG tidak ditemukan
                state = State.DISCONNECTED; device = null
                recordError = "${target.displayName} tidak memiliki layanan ECG/PPG yang dikenali."
                notifyUpdate(); return@postDelayed
            }
            try {
                val src = createSource()
                clearBuffers()
                source = src; sourceLabel = src.name
                state = State.CONNECTED
                prefs.lastDeviceAddress = target.address
                src.start(this)
            } catch (e: Exception) {
                state = State.DISCONNECTED; device = null
                recordError = "Gagal memuat data dummy: ${e.message}"
            }
            notifyUpdate()
        }, 1500)   // meniru waktu connect + discover service BLE
    }

    fun disconnect() {
        main.removeCallbacksAndMessages(null)
        stopRecording(partial = true)
        source?.stop(); source = null
        state = State.DISCONNECTED
        device = null
        leadOff = false; fingerPresent = false
        notifyUpdate()
    }

    private fun createSource(): DummySignalSource {
        val ecg = DummyData.openAsset(app, prefs.ecgRecord).use { DummyData.parseEcg(it) }
        val (ir, red) = DummyData.openAsset(app, prefs.ppgRecord).use { DummyData.parsePpg(it) }
        return DummySignalSource(
            ecg, ir, red, "simulasi: ${DummyData.label(prefs.ecgRecord)} + ${DummyData.label(prefs.ppgRecord)}",
        ).also { applyFlags(it) }
    }

    private fun applyFlags(s: DummySignalSource) {
        s.simulateDrops = prefs.simulateDrops
        s.simulateLeadOff = prefs.simulateLeadOff
    }

    /** Dipanggil dari Pengaturan: berlaku langsung pada sumber yang sedang berjalan. */
    fun refreshSourceFlags() { (source as? DummySignalSource)?.let { applyFlags(it) } }

    private fun clearBuffers() {
        ecgSeries.clear(); irSeries.clear(); redSeries.clear()
        ecgDet.reset(); ecgRate.reset(); ppgDet.reset(); ppgRate.reset()
        lastEcgT = 0.0; lastPpgT = 0.0; nextEcgIdx = -1; nextPpgIdx = -1; lastDeviceUs = 0
        ecgGapsLive = 0; ppgGapsLive = 0
    }

    // ---- paket masuk ---------------------------------------------------------------------

    override fun onEcg(startIndex: Long, startTimeUs: Long, adc: IntArray, leadOff: Boolean) {
        if (state != State.CONNECTED) return
        if (nextEcgIdx >= 0 && startIndex != nextEcgIdx) {
            ecgGapsLive++
            ecgDet.onGap(); ecgRate.breakChain()
            ecgSeries.add(startTimeUs / 1e6 - Config.ECG_PERIOD_US / 1e6 / 2, Float.NaN)   // putus garis
        }
        nextEcgIdx = startIndex + adc.size
        if (leadOff && !this.leadOff) { ecgDet.reset(); ecgRate.reset() }
        this.leadOff = leadOff
        for (i in adc.indices) {
            val idx = startIndex + i
            val t = (startTimeUs + i * Config.ECG_PERIOD_US) / 1e6
            if (leadOff) { ecgSeries.add(t, Float.NaN); continue }
            ecgSeries.add(t, adc[i].toFloat())
            val pk = ecgDet.process(adc[i], idx)
            if (pk != null) ecgRate.addPeak(pk * Config.ECG_PERIOD_US / 1e6)
        }
        lastEcgT = (startTimeUs + (adc.size - 1) * Config.ECG_PERIOD_US) / 1e6
        lastDeviceUs = maxOf(lastDeviceUs, startTimeUs)
        record { it.writeEcg(startIndex, startTimeUs, adc, leadOff) }
        notifyUpdate()
    }

    override fun onPpg(startIndex: Long, startTimeUs: Long, ir: IntArray, red: IntArray) {
        if (state != State.CONNECTED) return
        if (nextPpgIdx >= 0 && startIndex != nextPpgIdx) {
            ppgGapsLive++
            ppgDet.onGap(); ppgRate.breakChain()
            val tg = startTimeUs / 1e6 - Config.PPG_PERIOD_US / 1e6 / 2
            irSeries.add(tg, Float.NaN); redSeries.add(tg, Float.NaN)
        }
        nextPpgIdx = startIndex + ir.size
        fingerPresent = ir.last() >= Config.FINGER_MIN_IR
        for (i in ir.indices) {
            val t = (startTimeUs + i * Config.PPG_PERIOD_US) / 1e6
            if (ir[i] < Config.FINGER_MIN_IR) {
                irSeries.add(t, Float.NaN); redSeries.add(t, Float.NaN); continue
            }
            irSeries.add(t, ir[i].toFloat()); redSeries.add(t, red[i].toFloat())
            val pk = ppgDet.process(ir[i], startIndex + i)
            if (pk != null) ppgRate.addPeak(pk * Config.PPG_PERIOD_US / 1e6)
        }
        lastPpgT = (startTimeUs + (ir.size - 1) * Config.PPG_PERIOD_US) / 1e6
        lastDeviceUs = maxOf(lastDeviceUs, startTimeUs)
        record { it.writePpg(startIndex, startTimeUs, ir, red) }
        notifyUpdate()
    }

    // ---- rekaman -------------------------------------------------------------------------

    private inline fun record(block: (SessionRecorder) -> Unit) {
        val r = recorder ?: return
        try { block(r) } catch (e: IOException) {
            recordError = "Penyimpanan gagal (${e.message}); sesi disimpan parsial."
            stopRecording(partial = true)
        }
    }

    /** @return pesan error, atau null jika berhasil. */
    fun startRecording(): String? {
        if (state != State.CONNECTED) return "Sambungkan perangkat dulu."
        if (recorder != null) return null
        return try {
            recorder = SessionRecorder(repo.dir, lastDeviceUs, device?.displayName ?: Config.DEVICE_ID, device?.address ?: "")
            notifyUpdate(); null
        } catch (e: IOException) { "Tidak bisa membuat file rekaman: ${e.message}" }
    }

    fun stopRecording(partial: Boolean = false): SessionInfo? {
        val r = recorder ?: return null
        recorder = null
        val info = r.finish(partial, sourceLabel)
        exportAsync(info)
        notifyUpdate()
        return info
    }

    /** Salin CSV + metadata ke folder pilihan pengguna (jika ada), di thread terpisah. */
    private fun exportAsync(info: SessionInfo) {
        val tree = prefs.exportTreeUri?.let { Uri.parse(it) } ?: return
        val label = StorageExporter.folderLabel(tree)
        repo.setSyncStatus(info, "exported", label)      // ditulis sebelum menyalin agar salinan konsisten
        Thread {
            val res = runCatching { StorageExporter.export(app, tree, listOf(info.csvFile, info.metaFile)) }
            ui.post {
                res.onSuccess { notice = "Salinan sesi ${info.id} tersimpan di $label" }
                    .onFailure {
                        repo.setSyncStatus(info, "local_only")
                        notice = "Gagal menyalin ke folder pilihan: ${it.message}"
                    }
                notifyUpdate()
            }
        }.start()
    }
}
