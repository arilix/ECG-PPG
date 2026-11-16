package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.App
import com.example.myapplication.R
import com.example.myapplication.data.SessionInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDuration(s: Double): String =
    if (s < 0) "?" else "%d:%02d".format(s.toInt() / 60, s.toInt() % 60)

fun formatDate(ms: Long): String = SimpleDateFormat("d MMM yyyy, HH:mm:ss", Locale("id", "ID")).format(Date(ms))

class HistoryFragment : Fragment() {
    private lateinit var rv: RecyclerView
    private lateinit var empty: View
    private lateinit var count: TextView

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_history, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        v.findViewById<View>(R.id.btnMenu).setOnClickListener { (requireActivity() as com.example.myapplication.MainActivity).openDrawer() }
        rv = v.findViewById(R.id.rvSessions); empty = v.findViewById(R.id.tvEmpty); count = v.findViewById(R.id.tvCount)
        rv.layoutManager = LinearLayoutManager(requireContext())
    }

    override fun onStart() {
        super.onStart()
        val engine = (requireActivity().application as App).engine
        val items = engine.repo.list()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        count.text = if (items.isEmpty()) "" else "${items.size} sesi tersimpan di perangkat"
        rv.adapter = Adapter(items) { s ->
            startActivity(Intent(requireContext(), DetailActivity::class.java).putExtra(DetailActivity.EXTRA_ID, s.id))
        }
    }

    private class Adapter(val items: List<SessionInfo>, val onClick: (SessionInfo) -> Unit) :
        RecyclerView.Adapter<Adapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.tvTitle)
            val sub: TextView = v.findViewById(R.id.tvSub)
            val status: TextView = v.findViewById(R.id.tvStatus)
            val badge: TextView = v.findViewById(R.id.tvBadge)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_session, p, false))
        override fun getItemCount() = items.size
        override fun onBindViewHolder(h: VH, pos: Int) {
            val s = items[pos]
            h.title.text = formatDate(s.startedAtMs)
            h.sub.text = "${formatDuration(s.durationS)} menit · ECG ${s.ecgSamples} · PPG ${s.ppgSamples} sampel"
            val lost = s.ecgLost + s.ppgLost
            h.badge.text = when (s.syncStatus) { "local_only" -> "LOKAL"; "exported" -> "DISALIN"; else -> s.syncStatus.uppercase() }
            val ctx = h.itemView.context
            val warn = s.partial || lost > 0
            h.status.text = buildString {
                append("ID ${s.id}")
                if (s.partial) append(" · parsial")
                if (lost > 0) append(" · $lost sampel hilang")
            }
            h.status.setTextColor(ContextCompat.getColor(ctx, if (warn) R.color.status_warn else R.color.text_secondary))
            h.itemView.setOnClickListener { onClick(s) }
        }
    }
}
