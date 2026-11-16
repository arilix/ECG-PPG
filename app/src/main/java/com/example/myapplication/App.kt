package com.example.myapplication

import android.app.Application

class App : Application() {
    lateinit var engine: MonitorEngine
        private set

    override fun onCreate() {
        super.onCreate()
        engine = MonitorEngine(this)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(engine.prefs.themeMode)
    }
}
