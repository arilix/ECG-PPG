package com.example.myapplication.data

import android.content.Context
import com.example.myapplication.dsp.RPeakDetector
import com.example.myapplication.dsp.RateTracker
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** Metadata satu sesi (dibaca dari `<session_id>.metadata.json`). */
data class SessionInfo(
    val id: String,
    val csvFile: File,
    val metaFile: File,
    val startedAtMs: Long,
    val durationS: Double,       // < 0 = tidak diketahui (sesi tanpa metadata / crash)
    val ecgSamples: Long,
    val ppgSamples: Long,
    val ecgLost: Long,
    val ppgLost: Long,
    val partial: Boolean,
    val syncStatus: String,
    val source: String,
)

/** Menulis satu sesi ke satu CSV format long/sparse (lihat README bagian 9), secara bertahap. */
class SessionRecorder(dir: File, private val originUs: Long, private val deviceId: String, private val deviceAddress: String = "") {
    val sessionId: String = UUID.randomUUID().toString().replace("-", "").take(6)
    val startedAtMs: Long = System.currentTimeMillis()
    val csvFile: File
    private val metaFile: File
    private val writer: BufferedWriter

    var ecgRows = 0L; private set
    var ppgRows = 0L; private set
    var ecgLost = 0L; private set
    var ppgLost = 0L; private set
    private var nextEcg = -1L
    private var nextPpg = -1L
    private var lastUs = 0L
    private var batches = 0

    val durationS: Double get() = lastUs / 1e6

    init {
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date(startedAtMs))
        csvFile = File(dir, "ECGPPG_${stamp}_$sessionId.csv")
        metaFile = File(dir, "$sessionId.metadata.json")
        writer = BufferedWriter(FileWriter(csvFile))
        writer.write(Config.CSV_HEADER); writer.newLine()
    }

    @Throws(IOException::class)
    fun writeEcg(startIndex: Long, startTimeUs: Long, adc: IntArray, leadOff: Boolean) {
        if (nextEcg >= 0 && startIndex > nextEcg) ecgLost += startIndex - nextEcg
        nextEcg = startIndex + adc.size
        val sb = StringBuilder(adc.size * 56)
        val lo = if (leadOff) 1 else 0
        for (i in adc.indices) {
            val ts = startTimeUs + i * Config.ECG_PERIOD_US - originUs
            sb.append(sessionId).append(',').append(deviceId).append(",ecg,")
                .append(startIndex + i).append(',').append(ts).append(',').append(adc[i])
                .append(",,,").append(lo).append(',').append(1 - lo).append('\n')
            lastUs = maxOf(lastUs, ts)
        }
        ecgRows += adc.size
        writer.write(sb.toString()); flushSometimes()
    }

    @Throws(IOException::class)
    fun writePpg(startIndex: Long, startTimeUs: Long, ir: IntArray, red: IntArray) {
        if (nextPpg >= 0 && startIndex > nextPpg) ppgLost += startIndex - nextPpg
        nextPpg = startIndex + ir.size
        val sb = StringBuilder(ir.size * 56)
        for (i in ir.indices) {
            val ts = startTimeUs + i * Config.PPG_PERIOD_US - originUs
            val valid = if (ir[i] >= Config.FINGER_MIN_IR) 1 else 0
            sb.append(sessionId).append(',').append(deviceId).append(",ppg,")
                .append(startIndex + i).append(',').append(ts).append(",,")
                .append(ir[i]).append(',').append(red[i]).append(",,").append(valid).append('\n')
            lastUs = maxOf(lastUs, ts)
        }
        ppgRows += ir.size
        writer.write(sb.toString()); flushSometimes()
    }

    private fun flushSometimes() { if (++batches % 10 == 0) writer.flush() }

    /** Menutup file dan menulis metadata pendamping. */
    fun finish(partial: Boolean, sourceName: String): SessionInfo {
        try { writer.flush(); writer.close() } catch (_: IOException) { }
        val tz = TimeZone.getDefault()
        val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val json = JSONObject()
            .put("format_version", Config.CSV_FORMAT_VERSION)
            .put("session_id", sessionId)
            .put("device_id", deviceId)
            .put("device_address", deviceAddress)
            .put("csv_file", csvFile.name)
            .put("started_utc", utc.format(Date(startedAtMs)))
            .put("started_epoch_ms", startedAtMs)
            .put("timezone", tz.id)
            .put("duration_s", durationS)
            .put("target_ecg_hz", Config.ECG_FS)
            .put("target_ppg_hz", Config.PPG_FS)
            .put("ecg_samples", ecgRows)
            .put("ppg_samples", ppgRows)
            .put("ecg_lost", ecgLost)
            .put("ppg_lost", ppgLost)
            .put("partial", partial)
            .put("sync_status", "local_only")
            .put("source", sourceName)
        metaFile.writeText(json.toString(2))
        return SessionRepository.parseMeta(metaFile, csvFile)!!
    }
}

/** Daftar/hapus sesi di penyimpanan lokal aplikasi (`filesDir/recordings`). */
class SessionRepository(ctx: Context) {
    val dir = File(ctx.filesDir, "recordings")

    fun list(): List<SessionInfo> {
        val files = dir.listFiles() ?: return emptyList()
        val result = ArrayList<SessionInfo>()
        val withMeta = HashSet<String>()
        for (m in files.filter { it.name.endsWith(".metadata.json") }) {
            val csvName = runCatching { JSONObject(m.readText()).getString("csv_file") }.getOrNull() ?: continue
            withMeta.add(csvName)
            parseMeta(m, File(dir, csvName))?.let { result.add(it) }
        }
        // CSV tanpa metadata (mis. aplikasi mati saat merekam) tetap ditampilkan sebagai parsial.
        for (c in files.filter { it.name.endsWith(".csv") && it.name !in withMeta }) {
            val id = c.name.removeSuffix(".csv").substringAfterLast('_')
            result.add(SessionInfo(id, c, File(dir, "$id.metadata.json"), c.lastModified(), -1.0, 0, 0, 0, 0, true, "local_only", "?"))
        }
        return result.sortedByDescending { it.startedAtMs }
    }

    fun find(id: String): SessionInfo? = list().firstOrNull { it.id == id }

    fun delete(s: SessionInfo) { s.csvFile.delete(); s.metaFile.delete() }

    /** Memperbarui `sync_status` (mis. "exported") dan tujuan salinan di berkas metadata. */
    fun setSyncStatus(s: SessionInfo, status: String, exportedTo: String? = null) {
        runCatching {
            val j = JSONObject(s.metaFile.readText())
            j.put("sync_status", status)
            if (exportedTo != null) j.put("exported_to", exportedTo) else j.remove("exported_to")
            s.metaFile.writeText(j.toString(2))
        }
    }

    companion object {
        fun parseMeta(meta: File, csv: File): SessionInfo? = runCatching {
            val j = JSONObject(meta.readText())
            SessionInfo(
                id = j.getString("session_id"), csvFile = csv, metaFile = meta,
                startedAtMs = j.getLong("started_epoch_ms"), durationS = j.getDouble("duration_s"),
                ecgSamples = j.getLong("ecg_samples"), ppgSamples = j.getLong("ppg_samples"),
                ecgLost = j.getLong("ecg_lost"), ppgLost = j.getLong("ppg_lost"),
                partial = j.getBoolean("partial"), syncStatus = j.getString("sync_status"),
                source = j.optString("source", ""),
            )
        }.getOrNull()
    }
}

class CsvFormatException(message: String) : Exception(message)

class LoadedSession(
    val ecg: Series, val ir: Series, val red: Series,
    val ecgGaps: Int, val ppgGaps: Int, val badRows: Int,
    val avgEcgBpm: Int?,
)

/** Membaca CSV sesi dan menyusunnya kembali menjadi deret untuk line chart (gap/invalid -> putus garis). */
object CsvLoader {
    @Throws(IOException::class, CsvFormatException::class)
    fun load(file: File): LoadedSession {
        val ecg = Series(); val ir = Series(); val red = Series()
        var ecgGaps = 0; var ppgGaps = 0; var bad = 0
        var lastEcgT = Long.MIN_VALUE; var lastPpgT = Long.MIN_VALUE
        file.bufferedReader().use { r ->
            val header = r.readLine() ?: throw CsvFormatException("File kosong")
            if (header.trim() != Config.CSV_HEADER) throw CsvFormatException("Header CSV tidak dikenali")
            var line = r.readLine()
            while (line != null) {
                val p = line.split(',')
                line = r.readLine()
                if (p.size < 10) { bad++; continue }
                try {
                    val ts = p[4].toLong()
                    val valid = p[9] == "1"
                    val x = ts / 1e6
                    when (p[2]) {
                        "ecg" -> {
                            if (lastEcgT != Long.MIN_VALUE && ts - lastEcgT > 2.5 * Config.ECG_PERIOD_US) {
                                ecgGaps++; ecg.add(lastEcgT / 1e6 + 0.001, Float.NaN)
                            }
                            ecg.add(x, if (valid) p[5].toFloat() else Float.NaN)
                            lastEcgT = ts
                        }
                        "ppg" -> {
                            if (lastPpgT != Long.MIN_VALUE && ts - lastPpgT > 2.5 * Config.PPG_PERIOD_US) {
                                ppgGaps++
                                ir.add(lastPpgT / 1e6 + 0.001, Float.NaN); red.add(lastPpgT / 1e6 + 0.001, Float.NaN)
                            }
                            ir.add(x, if (valid) p[6].toFloat() else Float.NaN)
                            red.add(x, if (valid) p[7].toFloat() else Float.NaN)
                            lastPpgT = ts
                        }
                        else -> bad++
                    }
                } catch (_: NumberFormatException) { bad++ }
            }
        }
        if (ecg.size == 0 && ir.size == 0) throw CsvFormatException("Tidak ada data sampel valid")
        return LoadedSession(ecg, ir, red, ecgGaps, ppgGaps, bad, averageBpm(ecg))
    }

    /** BPM rata-rata ECG dari DSP yang sama dengan live (detektor di-reset di tiap gap). */
    private fun averageBpm(ecg: Series): Int? {
        if (ecg.size < Config.ECG_FS * 10) return null
        val det = RPeakDetector(Config.ECG_FS.toDouble())
        val rate = RateTracker()
        var peaks = 0; var first = Double.NaN; var last = Double.NaN
        for (i in 0 until ecg.size) {
            val y = ecg.ys[i]
            if (y.isNaN()) { det.onGap(); rate.breakChain(); continue }
            val pk = det.process(y.roundToInt(), i.toLong()) ?: continue
            val t = ecg.xs[minOf(pk.toInt(), ecg.size - 1)]
            rate.addPeak(t); peaks++
            if (first.isNaN()) first = t
            last = t
        }
        if (peaks < 5 || last <= first) return null
        return ((peaks - 1) * 60.0 / (last - first)).toInt()
    }

    private fun Float.roundToInt() = Math.round(this)
}
