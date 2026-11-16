package com.example.myapplication.ui

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButtonToggleGroup
import androidx.fragment.app.Fragment
import com.example.myapplication.App
import com.example.myapplication.MonitorEngine
import com.example.myapplication.R
import com.example.myapplication.data.DummyAccounts
import com.example.myapplication.data.DummyData
import com.example.myapplication.data.StorageExporter
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

/** Pengaturan (tampil di drawer samping): akun Google (simulasi), lokasi penyimpanan, data dummy, tampilan. */
class SettingsFragment : Fragment() {
    private lateinit var engine: MonitorEngine
    private lateinit var tvAvatar: TextView
    private lateinit var tvAccName: TextView
    private lateinit var tvAccSub: TextView
    private lateinit var btnAccount: MaterialButton
    private lateinit var tvStoreTitle: TextView
    private lateinit var tvStorePath: TextView
    private lateinit var btnReset: MaterialButton

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        engine.prefs.exportTreeUri = uri.toString()
        renderAccountAndStorage()
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_settings, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        engine = (requireActivity().application as App).engine
        val prefs = engine.prefs
        val ctx = requireContext()

        // ---- akun & penyimpanan ----
        tvAvatar = v.findViewById(R.id.tvAvatar); tvAccName = v.findViewById(R.id.tvAccName)
        tvAccSub = v.findViewById(R.id.tvAccSub); btnAccount = v.findViewById(R.id.btnAccount)
        tvStoreTitle = v.findViewById(R.id.tvStoreTitle); tvStorePath = v.findViewById(R.id.tvStorePath)
        btnReset = v.findViewById(R.id.btnResetFolder)
        btnAccount.setOnClickListener { if (prefs.googleEmail == null) showAccountPicker() else signOut() }
        v.findViewById<View>(R.id.btnPickFolder).setOnClickListener { pickFolder.launch(null) }
        btnReset.setOnClickListener { prefs.exportTreeUri = null; renderAccountAndStorage() }
        renderAccountAndStorage()

        // ---- data dummy & tampilan ----
        fun bindSpinner(sp: Spinner, labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
            sp.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, labels)
            sp.setSelection(selected.coerceAtLeast(0))
            // Hanya terima pilihan dari sentuhan pengguna: Spinner mengirim callback awal
            // (kadang berulang) saat layout, yang tidak boleh menimpa preferensi.
            var touched = false
            sp.setOnTouchListener { _, _ -> touched = true; false }
            sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    if (!touched) return
                    onPick(pos)
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        val ecg = DummyData.ecgRecords(ctx)
        bindSpinner(v.findViewById(R.id.spEcg), ecg.map { DummyData.label(it) }, ecg.indexOf(prefs.ecgRecord)) { prefs.ecgRecord = ecg[it] }
        val ppg = DummyData.ppgRecords(ctx)
        bindSpinner(v.findViewById(R.id.spPpg), ppg.map { DummyData.label(it) }, ppg.indexOf(prefs.ppgRecord)) { prefs.ppgRecord = ppg[it] }
        val windows = listOf(5, 10, 20)
        bindSpinner(v.findViewById(R.id.spWindow), windows.map { "$it detik" }, windows.indexOf(prefs.windowSec)) { prefs.windowSec = windows[it] }

        // ---- tema (Sistem / Terang / Gelap) ----
        val toggle = v.findViewById<MaterialButtonToggleGroup>(R.id.toggleTheme)
        toggle.check(when (prefs.themeMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.id.btnThemeLight
            AppCompatDelegate.MODE_NIGHT_YES -> R.id.btnThemeDark
            else -> R.id.btnThemeSystem
        })
        toggle.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            val mode = when (id) {
                R.id.btnThemeLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.btnThemeDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            if (mode != prefs.themeMode) { prefs.themeMode = mode; AppCompatDelegate.setDefaultNightMode(mode) }   // Activity dibuat ulang otomatis
        }

        v.findViewById<MaterialSwitch>(R.id.swDrops).apply {
            isChecked = prefs.simulateDrops
            setOnCheckedChangeListener { _, on -> prefs.simulateDrops = on; engine.refreshSourceFlags() }
        }
        v.findViewById<MaterialSwitch>(R.id.swLeadOff).apply {
            isChecked = prefs.simulateLeadOff
            setOnCheckedChangeListener { _, on -> prefs.simulateLeadOff = on; engine.refreshSourceFlags() }
        }
        v.findViewById<TextView>(R.id.tvSettingsNote).text =
            "Pilihan rekaman ECG/PPG berlaku pada sambungan berikutnya (putuskan lalu sambungkan lagi)."
    }

    private fun showAccountPicker() {
        val ctx = requireContext()
        val accounts = DummyAccounts.list
        val dp = resources.displayMetrics.density
        // Catatan ditaruh di judul kustom: setMessage() pada AlertDialog akan menyembunyikan daftar item.
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt(), (8 * dp).toInt())
            addView(TextView(ctx).apply { text = "Masuk dengan Google (simulasi)"; textSize = 20f; setTypeface(typeface, Typeface.BOLD) })
            addView(TextView(ctx).apply {
                text = "Akun di bawah hanya contoh. Login Google dan Drive sungguhan belum diimplementasikan."
                textSize = 12f; setPadding(0, (6 * dp).toInt(), 0, 0)
            })
        }
        AlertDialog.Builder(ctx)
            .setCustomTitle(header)
            .setItems(accounts.map { "${it.name}\n${it.email}" }.toTypedArray()) { _, i ->
                engine.prefs.googleName = accounts[i].name
                engine.prefs.googleEmail = accounts[i].email
                renderAccountAndStorage()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun signOut() {
        engine.prefs.googleName = null; engine.prefs.googleEmail = null; renderAccountAndStorage()
    }

    private fun renderAccountAndStorage() {
        val prefs = engine.prefs
        val email = prefs.googleEmail; val name = prefs.googleName
        if (email == null) {
            tvAvatar.text = "?"; tvAccName.text = "Belum masuk"; tvAccSub.text = "Untuk cadangan ke Google Drive"
            btnAccount.text = "Masuk"
        } else {
            tvAvatar.text = (name ?: email).first().uppercase(); tvAccName.text = name ?: email
            tvAccSub.text = email; btnAccount.text = "Keluar"
        }
        val tree = prefs.exportTreeUri?.let { Uri.parse(it) }
        if (tree == null) {
            tvStoreTitle.text = "Penyimpanan internal aplikasi"
            tvStorePath.text = "Privat; tidak butuh izin storage"
            btnReset.visibility = View.GONE
        } else {
            tvStoreTitle.text = "Internal + salinan ke folder"
            tvStorePath.text = StorageExporter.folderLabel(tree)
            btnReset.visibility = View.VISIBLE
        }
    }
}
