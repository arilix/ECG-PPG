package com.example.myapplication

import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.example.myapplication.ui.DevicesFragment
import com.example.myapplication.ui.HistoryFragment
import com.example.myapplication.ui.LiveFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity(), MonitorEngine.Listener {
    private lateinit var drawer: DrawerLayout
    private lateinit var engine: MonitorEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        engine = (application as App).engine
        drawer = findViewById(R.id.drawer)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, 0)   // inset bawah ditangani BottomNavigationView
            insets
        }
        // Drawer menutupi area di bawah status bar sendiri, jadi beri padding atas/bawah di dalamnya.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawerContainer)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            show(
                when (item.itemId) {
                    R.id.nav_devices -> DevicesFragment()
                    R.id.nav_history -> HistoryFragment()
                    else -> LiveFragment()
                }
            )
            true
        }
        if (savedInstanceState == null) show(LiveFragment())

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.START)) drawer.closeDrawer(GravityCompat.START)
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
    }

    /** Membuka drawer samping = Pengaturan (akun Google, lokasi penyimpanan, data dummy, tampilan). */
    fun openDrawer() { drawer.openDrawer(GravityCompat.START) }

    /** Berpindah ke tab Perangkat (pencarian BLE). */
    fun openDevices() {
        findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_devices
    }

    private fun show(f: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.container, f).commit()
    }

    // Pesan sekali-tampil dari engine (error koneksi/penyimpanan, hasil penyalinan) ditampilkan di sini
    // supaya muncul di tab/drawer mana pun.
    override fun onStart() { super.onStart(); engine.addListener(this); onEngineUpdate() }
    override fun onStop() { engine.removeListener(this); super.onStop() }
    override fun onEngineUpdate() {
        engine.recordError?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show(); engine.recordError = null }
        engine.notice?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show(); engine.notice = null }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Aplikasi ditutup (bukan rotasi): hentikan simulasi dan simpan rekaman yang berjalan sebagai parsial.
        if (isFinishing) engine.disconnect()
    }
}
