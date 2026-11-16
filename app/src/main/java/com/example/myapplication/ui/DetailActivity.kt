package com.example.myapplication.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.App
import com.example.myapplication.R
import com.example.myapplication.data.CsvLoader
import com.example.myapplication.data.SessionInfo
import com.google.android.material.appbar.MaterialToolbar

/** Detail rekaman: baca CSV, susun ulang grafik ECG/PPG dengan zoom/pan (sinkron antar-grafik). */
class DetailActivity : AppCompatActivity() {
    private lateinit var info: SessionInfo
    private lateinit var tvInfo: TextView
    private lateinit var charts: List<WaveformView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_detail)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailRoot)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom); insets
        }
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val id = intent.getStringExtra(EXTRA_ID)
        val found = id?.let { (application as App).engine.repo.find(it) }
        if (found == null) { finish(); return }
        info = found
        title = "Sesi ${info.id}"

        tvInfo = findViewById(R.id.tvDetailInfo)
        val ecg = findViewById<WaveformView>(R.id.dEcg)
        val ir = findViewById<WaveformView>(R.id.dIr)
        val red = findViewById<WaveformView>(R.id.dRed)
        charts = listOf(ecg, ir, red)
        ecg.title = "ECG"; ecg.subtitle = "ADC 0–4095"; ecg.lineColor = Color.parseColor("#3DDC84")
        ir.title = "PPG IR"; ir.subtitle = "MAX30102"; ir.lineColor = Color.parseColor("#4FC3F7")
        red.title = "PPG Red"; red.subtitle = "MAX30102"; red.lineColor = Color.parseColor("#FF6E6E")
        charts.forEach { c ->
            c.live = false
            c.onWindowChanged = { a, b -> charts.filter { it !== c }.forEach { it.setWindow(a, b) } }
        }
        tvInfo.text = "Memuat CSV…"

        Thread {
            val result = runCatching { CsvLoader.load(info.csvFile) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                result.onSuccess { s ->
                    ecg.series = s.ecg; ir.series = s.ir; red.series = s.red
                    charts.forEach { it.fitAll() }
                    findViewById<TextView>(R.id.tvStatDur).text = formatDuration(info.durationS)
                    findViewById<TextView>(R.id.tvStatBpm).text = s.avgEcgBpm?.toString() ?: "--"
                    findViewById<TextView>(R.id.tvStatGap).text = (s.ecgGaps + s.ppgGaps).toString()
                    tvInfo.text = buildString {
                        append(formatDate(info.startedAtMs))
                        append(" · ECG ${s.ecg.size} · PPG ${s.ir.size} baris")
                        if (s.ecgGaps + s.ppgGaps > 0) append("\nGap — ECG ${s.ecgGaps}, PPG ${s.ppgGaps}: garis sengaja diputus")
                        if (s.badRows > 0) append("\n${s.badRows} baris rusak dilewati")
                        if (info.partial) append("\nSesi parsial")
                    }
                }.onFailure { e ->
                    tvInfo.text = "Gagal membuka CSV: ${e.message}"
                }
            }
        }.start()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.detail, menu); return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.action_share -> { share(); true }
        R.id.action_delete -> { confirmDelete(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun share() {
        val auth = "$packageName.fileprovider"
        val uris = ArrayList(listOfNotNull(info.csvFile, info.metaFile.takeIf { it.exists() })
            .map { FileProvider.getUriForFile(this, auth, it) })
        val i = Intent(Intent.ACTION_SEND_MULTIPLE).setType("text/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(i, "Bagikan rekaman"))
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Hapus rekaman?")
            .setMessage("File CSV dan metadata sesi ${info.id} akan dihapus dari penyimpanan lokal.")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Hapus") { _, _ ->
                (application as App).engine.repo.delete(info); finish()
            }.show()
    }

    companion object { const val EXTRA_ID = "session_id" }
}
