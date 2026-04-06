package com.example.nemebudget

import android.app.Application

class NemeBudgetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PersistentShaderCache.initialize(this)
    }
}

