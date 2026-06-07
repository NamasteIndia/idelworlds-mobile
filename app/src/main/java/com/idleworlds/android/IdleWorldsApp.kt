package com.idleworlds.android

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class IdleWorldsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
}
