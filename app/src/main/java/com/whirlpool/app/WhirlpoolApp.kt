package com.whirlpool.app

import android.app.Application

class WhirlpoolApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}

