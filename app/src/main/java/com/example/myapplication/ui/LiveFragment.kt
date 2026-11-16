package com.example.myapplication.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import com.google.android.material.button.MaterialButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.myapplication.App
import com.example.myapplication.MonitorEngine
import com.example.myapplication.MonitorEngine.State
import com.example.myapplication.R

class LiveFragment : Fragment(), MonitorEngine.Listener {
    private lateinit var engine: MonitorEngine
    private lateinit var tvConn: TextView
    private lateinit var tvRec: TextView
    private lateinit var tvEcgBpm: TextView
    private lateinit var tvPpgBpm: TextView
    private lateinit var tvSpo2: TextView
    private lateinit var tvHrv: TextView
    private lateinit var tvInfo: TextView
    private lateinit var btnConnect: MaterialButton
    private lateinit var tvSubtitle: TextView
    private lateinit var rowMetrics: View
    private var recBlink: ObjectAnimator? = null
    private lateinit var btnRecord: MaterialButton
    private lateinit var chEcg: WaveformView
    private lateinit var chIr: WaveformView
    private lateinit var chRed: WaveformView

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_live, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        engine = (requireActivity().application as App).engine
        tvConn = v.findViewById(R.id.tvConn); tvRec = v.findViewById(R.id.tvRec)
        tvEcgBpm = v.findViewById(R.id.tvEcgBpm); tvPpgBpm = v.findViewById(R.id.tvPpgBpm)
        tvSpo2 = v.findViewById(R.id.tvSpo2); tvHrv = v.findViewById(R.id.tvHrv)
        tvInfo = v.findViewById(R.id.tvInfo); tvSubtitle = v.findViewById(R.id.tvSubtitle); rowMetrics = v.findViewById(R.id.rowMetrics)
        v.findViewById<View>(R.id.btnMenu).setOnClickListener { (requireActivity() as com.example.myapplication.MainActivity).openDrawer() }
        btnConnect = v.findViewById(R.id.btnConnect); btnRecord = v.findViewById(R.id.btnRecord)
        chEcg = v.findViewById(R.id.chEcg); chIr = v.findViewById(R.id.chIr); chRed = v.findViewById(R.id.chRed)

        chEcg.title = "ECG"; chEcg.subtitle = "AD8232 · 250 Hz"; chEcg.series = engine.ecgSeries
        chEcg.lineColor = ContextCompat.getColor(requireContext(), R.color.chart_ecg)
        chIr.title = "PPG IR"; chIr.subtitle = "MAX30102 · 100 Hz"; chIr.series = engine.irSeries
        chIr.lineColor = ContextCompat.getColor(requireContext(), R.color.chart_ir)
        chRed.title = "PPG Red"; chRed.subtitle = "MAX30102 · 100 Hz"; chRed.series = engine.redSeries
        chRed.lineColor = ContextCompat.getColor(requireContext(), R.color.chart_red)

        btnConnect.setOnClickListener {
            if (engine.state == State.DISCONNECTED) (requireActivity() as com.example.myapplication.MainActivity).openDevices()
            else engine.disconnect()
        }
        btnRecord.setOnClickListener {
            if (engine.recorder == null) {
                engine.startRecording()?.let { toast(it) }
            } else {
                val info = engine.stopRecording()
                toast(if (info != null) "Sesi ${info.id} tersimpan di Riwayat" else "Gagal menyimpan")
            }
        }
    }

    override fun onStart() { super.onStart(); engine.addListener(this); render() }
    override fun onStop() { engine.removeListener(this); recBlink?.cancel(); recBlink = null; super.onStop() }

    override fun onEngineUpdate() { if (view != null) render() }

    private fun toast(m: String) = Toast.makeText(requireContext(), m, Toast.LENGTH_LONG).show()

    private fun render() {
        val w = engine.prefs.windowSec.toDouble()
        listOf(chEcg, chIr, chRed).forEach { it.windowSec = w; it.invalidate() }
        chEcg.message = if (engine.leadOff) "Elektroda terlepas (lead-off)" else null
        chIr.message = if (engine.state == State.CONNECTED && !engine.fingerPresent) "Jari tidak terdeteksi" else null
        chRed.message = chIr.message

        val st = engine.state
        val ctx = requireContext()
        tvConn.text = when (st) {
            State.DISCONNECTED -> "● Terputus"
            State.CONNECTING -> "● Menghubungkan…"
            State.CONNECTED -> "● Terhubung"
        }
        tvConn.setTextColor(ContextCompat.getColor(ctx, when (st) {
            State.CONNECTED -> R.color.status_ok
            State.CONNECTING -> R.color.status_warn
            else -> R.color.status_bad
        }))
        tvSubtitle.text = engine.device?.let { "${it.displayName} · ${it.address}" } ?: "Belum ada perangkat terhubung"
        btnConnect.isEnabled = st != State.CONNECTING
        btnConnect.setIconResource(if (st == State.DISCONNECTED) R.drawable.ic_search else R.drawable.ic_link)
        btnConnect.text = when (st) {
            State.DISCONNECTED -> "Cari Perangkat"
            State.CONNECTING -> "Menghubungkan…"
            State.CONNECTED -> "Putuskan"
        }
        val rec = engine.recorder
        btnRecord.isEnabled = st == State.CONNECTED
        btnRecord.alpha = if (st == State.CONNECTED) 1f else 0.5f
        if (rec == null) {
            btnRecord.text = "Mulai Rekam"
            btnRecord.setIconResource(R.drawable.ic_rec)
            btnRecord.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.brand_container))
            btnRecord.setTextColor(ContextCompat.getColor(ctx, R.color.on_brand_container))
            btnRecord.iconTint = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.status_bad))
            recBlink?.cancel(); recBlink = null; tvRec.alpha = 1f
        } else {
            btnRecord.text = "Selesai Rekam"
            btnRecord.setIconResource(R.drawable.ic_stop)
            btnRecord.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.status_bad))
            btnRecord.setTextColor(Color.WHITE)
            btnRecord.iconTint = ColorStateList.valueOf(Color.WHITE)
            if (recBlink == null) recBlink = ObjectAnimator.ofFloat(tvRec, "alpha", 1f, 0.25f).apply {
                duration = 700; repeatCount = ObjectAnimator.INFINITE; repeatMode = ObjectAnimator.REVERSE; start()
            }
        }
        tvRec.text = if (rec == null) "" else "● REC %d:%02d".format(rec.durationS.toInt() / 60, rec.durationS.toInt() % 60)
        rowMetrics.alpha = if (st == State.CONNECTED) 1f else 0.45f

        val connected = st == State.CONNECTED
        tvEcgBpm.text = if (connected && !engine.leadOff) engine.ecgBpm()?.toString() ?: "--" else "--"
        tvPpgBpm.text = if (connected && engine.fingerPresent) engine.ppgBpm()?.toString() ?: "--" else "--"
        tvSpo2.text = "--"      // belum diimplementasikan/dikalibrasi (lihat README)
        tvHrv.text = if (connected && !engine.leadOff) engine.rmssdMs()?.toString() ?: "--" else "--"

        tvInfo.text = buildString {
            if (!connected) append("Tekan Cari Perangkat untuk menyambung. Pengaturan: ☰ atau geser dari kiri. Bukan alat medis.")
            else {
                append(engine.rrMs()?.let { "RR terakhir $it ms" } ?: "Mengukur…")
                if (engine.ecgGapsLive + engine.ppgGapsLive > 0)
                    append("  ·  ⚠ paket hilang: ECG ${engine.ecgGapsLive}, PPG ${engine.ppgGapsLive}")
            }
        }
    }
}
