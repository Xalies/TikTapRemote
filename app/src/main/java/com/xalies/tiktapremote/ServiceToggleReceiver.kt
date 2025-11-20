package com.xalies.tiktapremote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ServiceToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repository = ProfileRepository(context)
        val newStatus = !repository.isServiceEnabled()
        repository.setServiceEnabled(newStatus)

        // The receiver is now responsible for updating the notification UI directly.
        showControlNotification(context, newStatus)
    }
}