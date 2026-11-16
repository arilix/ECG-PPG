package com.example.myapplication.data

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var ecgRecord: String
        get() = sp.getString("ecg_record", "ecg_mitdb_100.txt")!!
        set(v) = sp.edit().putString("ecg_record", v).apply()
    var ppgRecord: String
        get() = sp.getString("ppg_record", "ppg_bidmc_01.txt")!!
        set(v) = sp.edit().putString("ppg_record", v).apply()
    var windowSec: Int
        get() = sp.getInt("window_sec", 10)
        set(v) = sp.edit().putInt("window_sec", v).apply()
    var simulateDrops: Boolean
        get() = sp.getBoolean("sim_drops", false)
        set(v) = sp.edit().putBoolean("sim_drops", v).apply()
    var simulateLeadOff: Boolean
        get() = sp.getBoolean("sim_leadoff", false)
        set(v) = sp.edit().putBoolean("sim_leadoff", v).apply()
    var lastDeviceAddress: String?
        get() = sp.getString("last_device_addr", null)
        set(v) = sp.edit().putString("last_device_addr", v).apply()
    /** URI folder tujuan salinan rekaman (SAF), null = hanya penyimpanan internal. */
    var exportTreeUri: String?
        get() = sp.getString("export_tree", null)
        set(v) = sp.edit().putString("export_tree", v).apply()
    var googleName: String?
        get() = sp.getString("google_name", null)
        set(v) = sp.edit().putString("google_name", v).apply()
    var googleEmail: String?
        get() = sp.getString("google_email", null)
        set(v) = sp.edit().putString("google_email", v).apply()
    /** Mode tema: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM (-1), MODE_NIGHT_NO (1), MODE_NIGHT_YES (2). */
    var themeMode: Int
        get() = sp.getInt("theme_mode", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(v) = sp.edit().putInt("theme_mode", v).apply()
}
