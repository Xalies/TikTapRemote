package com.xalies.tiktapremote

import android.app.Application

class TikTapRemoteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ProfileManager.initialize(this)
        BillingManager.initialize(this) // Initialize Billing
    }
}