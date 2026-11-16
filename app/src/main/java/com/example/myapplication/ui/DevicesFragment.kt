package com.example.myapplication.ui

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.App
import com.example.myapplication.MainActivity
import com.example.myapplication.MonitorEngine
import com.example.myapplication.MonitorEngine.State
import com.example.myapplication.R
import com.example.myapplication.data.ScannedDevice
import com.google.android.material.button.MaterialButton

/** Tab Perangkat: cari perangkat BLE (simulasi), lihat kekuatan sinyal, pilih untuk menyambung. */
class DevicesFragment : Fragment(), MonitorEngine.Listener {
    private lateinit var engine: MonitorEngine
    private lateinit var adapter: Adapter
    private lateinit var btnScan: MaterialButton
    private lateinit var btnDisconnect: MaterialButton
    private lateinit var progress: View
    private lateinit var card: View
    private lateinit var tvCurName: TextView
    private lateinit var tvCurSub: TextView
    private lateinit var tvCount: TextView
    private lateinit var tvHint: TextView
    private lateinit var empty: View
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptySub: TextView

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_devices, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        engine = (requireActivity().application as App).engine
        v.findViewById<View>(R.id.btnMenu).setOnClickListener { (requireActivity() as MainActivity).openDrawer() }
        btnScan = v.findViewById(R.id.btnScan); btnDisconnect = v.findViewById(R.id.btnDisconnect)
        progress = v.findViewById(R.id.progress); card = v.findViewById(R.id.cardCurrent)
        tvCurName = v.findViewById(R.id.tvCurName); tvCurSub = v.findViewById(R.id.tvCurSub)
        tvCount = v.findViewById(R.id.tvDevCount); tvHint = v.findViewById(R.id.tvDevHint)
        empty = v.findViewById(R.id.emptyDevices)
        tvEmptyTitle = v.findViewById(R.id.tvEmptyTitle); tvEmptySub = v.findViewById(R.id.tvEmptySub)

        adapter = Adapter { d ->
            if (engine.state != State.DISCONNECTED) {
                Toast.makeText(requireContext(), "Putuskan koneksi saat ini dulu.", Toast.LENGTH_SHORT).show()
            } else engine.connect(d)
        }
        v.findViewById<RecyclerView>(R.id.rvDevices).apply {
            layoutManager = LinearLayoutManager(requireContext()); adapter = this@DevicesFragment.adapter; itemAnimator = null
        }
        btnScan.setOnClickListener { if (engine.scanning) engine.stopScan() else engine.startScan() }
        btnDisconnect.setOnClickListener { engine.disconnect() }
    }

    override fun onStart() {
        super.onStart()
        engine.addListener(this)
        // Masuk tab ini dalam keadaan terputus dan belum pernah memindai -> langsung cari.
        if (engine.state == State.DISCONNECTED && engine.devices.isEmpty()) engine.startScan()
        render()
    }

    override fun onStop() { engine.removeListener(this); super.onStop() }
    override fun onEngineUpdate() { if (view != null) render() }

    private fun render() {
        val st = engine.state; val cur = engine.device
        card.visibility = if (st != State.DISCONNECTED && cur != null) View.VISIBLE else View.GONE
        if (cur != null) {
            tvCurName.text = cur.displayName
            tvCurSub.text = if (st == State.CONNECTING) "Menghubungkan…  ·  ${cur.address}" else "Terhubung  ·  ${engine.sourceLabel}"
        }
        btnDisconnect.text = if (st == State.CONNECTING) "Batal" else "Putuskan"
        progress.visibility = if (engine.scanning || st == State.CONNECTING) View.VISIBLE else View.INVISIBLE
        btnScan.isEnabled = st == State.DISCONNECTED
        btnScan.text = if (engine.scanning) "Hentikan Pencarian" else "Cari Perangkat"
        btnScan.setIconResource(if (engine.scanning) R.drawable.ic_stop else R.drawable.ic_search)
        tvHint.text = if (st != State.DISCONNECTED) "Putuskan koneksi untuk mencari perangkat lain."
        else "Data perangkat masih simulasi (dummy). Pencarian BLE nyata menyusul."

        val sorted = engine.devices.sortedWith(compareByDescending<ScannedDevice> { it.compatible }.thenByDescending { it.rssi })
        adapter.submit(sorted, engine.prefs.lastDeviceAddress, if (st == State.CONNECTING) cur?.address else null)
        val n = sorted.size
        tvCount.text = when {
            engine.scanning -> "Mencari… $n perangkat ditemukan"
            n > 0 -> "$n perangkat ditemukan"
            else -> "Belum ada hasil pencarian"
        }
        empty.visibility = if (n == 0) View.VISIBLE else View.GONE
        if (engine.scanning) { tvEmptyTitle.text = "Mencari perangkat…"; tvEmptySub.text = "Pastikan alat menyala dan dekat dengan ponsel." }
        else { tvEmptyTitle.text = "Tidak ada perangkat"; tvEmptySub.text = "Tekan Cari Perangkat untuk memindai lagi." }
    }

    private class Adapter(val onClick: (ScannedDevice) -> Unit) : RecyclerView.Adapter<Adapter.VH>() {
        private var items: List<ScannedDevice> = emptyList()
        private var lastAddr: String? = null
        private var connecting: String? = null

        fun submit(list: List<ScannedDevice>, last: String?, connectingAddr: String?) {
            items = list; lastAddr = last; connecting = connectingAddr; notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvName)
            val addr: TextView = v.findViewById(R.id.tvAddr)
            val badge: TextView = v.findViewById(R.id.tvBadge)
            val bars: TextView = v.findViewById(R.id.tvBars)
            val rssi: TextView = v.findViewById(R.id.tvRssi)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.item_device, p, false))
        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val d = items[pos]; val ctx = h.itemView.context
            h.name.text = d.displayName
            h.addr.text = d.address
            h.rssi.text = "${d.rssi} dBm"
            val brand = ContextCompat.getColor(ctx, R.color.brand)
            val dim = ContextCompat.getColor(ctx, R.color.card_stroke)
            h.bars.text = SpannableString("▂▄▆█").apply {
                setSpan(ForegroundColorSpan(brand), 0, d.signalLevel, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (d.signalLevel < 4) setSpan(ForegroundColorSpan(dim), d.signalLevel, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            h.badge.text = when {
                connecting == d.address -> "MENGHUBUNGKAN…"
                d.compatible && d.address == lastAddr -> "KOMPATIBEL · TERAKHIR"
                d.compatible -> "KOMPATIBEL"
                else -> "TIDAK DIKENAL"
            }
            h.badge.setTextColor(ContextCompat.getColor(ctx, if (d.compatible) R.color.brand else R.color.text_secondary))
            h.itemView.alpha = if (d.compatible) 1f else 0.7f
            h.itemView.setOnClickListener { onClick(d) }
        }
    }
}
