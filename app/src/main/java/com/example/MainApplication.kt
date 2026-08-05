package com.example

import android.app.Application
import android.util.Log
import com.example.util.AdMobManager

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("MainApplication", "========================================")
        Log.d("MainApplication", "🚀 Initializing Application & AdMob SDK...")
        Log.d("MainApplication", "========================================")

        try {
            AdMobManager.init(this)
            Log.d("MainApplication", "✅ AdMob SDK initialized successfully from MainApplication.")
        } catch (e: Exception) {
            Log.e("MainApplication", "❌ Error initializing AdMob SDK: ${e.message}", e)
        }
    }
}
