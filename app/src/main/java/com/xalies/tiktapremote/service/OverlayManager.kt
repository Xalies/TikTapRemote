package com.xalies.tiktapremote.service

import android.content.Context
import android.content.Intent
import com.xalies.tiktapremote.OverlayActivity

class OverlayManager(private val context: Context) {

    fun showOverlay(
        mode: String,
        targetPackageName: String,
        targetAppName: String,
        selectedTrigger: String,
        keyCode: Int,
        blockInput: Boolean,
        existingX: Int,
        existingY: Int,
        singleAction: String?,
        doubleAction: String?
    ) {
        val intent = Intent(context, OverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("mode", mode)
            putExtra("targetPackageName", targetPackageName)
            putExtra("targetAppName", targetAppName)
            putExtra("selectedTrigger", selectedTrigger)
            putExtra("keyCode", keyCode)
            putExtra("blockInput", blockInput)
            putExtra("tapX", existingX)
            putExtra("tapY", existingY)
            if (singleAction != null) putExtra("singleAction", singleAction)
            if (doubleAction != null) putExtra("doubleAction", doubleAction)
        }
        context.startActivity(intent)
    }

    fun removeOverlay() {
        // OverlayActivity handles its own closure via broadcast or finish()
    }

    fun onDestroy() {
        // No cleanup needed for activity launch
    }
}