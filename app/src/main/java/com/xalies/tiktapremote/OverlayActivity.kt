package com.xalies.tiktapremote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.xalies.tiktapremote.ui.theme.TikTapRemoteTheme
import java.net.URLEncoder

class OverlayActivity : ComponentActivity() {

    private var targetPackageName = ""
    private var targetAppName = ""
    private var selectedTrigger = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val mode = intent.getStringExtra("mode") ?: "targeting"
        targetPackageName = intent.getStringExtra("targetPackageName") ?: ""
        targetAppName = intent.getStringExtra("targetAppName") ?: ""
        selectedTrigger = intent.getStringExtra("selectedTrigger") ?: TriggerType.SINGLE_PRESS.name

        val keyCode = intent.getIntExtra("keyCode", -1)
        val blockInput = intent.getBooleanExtra("blockInput", false)
        val existingX = intent.getIntExtra("tapX", 0)
        val existingY = intent.getIntExtra("tapY", 0)
        val singleAction = intent.getStringExtra("singleAction")
        val doubleAction = intent.getStringExtra("doubleAction")

        setContent {
            TikTapRemoteTheme {
                if (mode == "targeting") {
                    OverlayView(
                        mode = "targeting",
                        onConfirmTarget = { x, y ->
                            finishWithResult(x, y, null, keyCode, blockInput, singleAction, doubleAction)
                        },
                        onCancel = {
                            cancelSetTargetNotification(this)
                            sendBroadcast(Intent(ACTION_STOP_TARGETING))
                            finish()
                        }
                    )
                } else {
                    OverlayView(
                        mode = "recording",
                        onGestureRecorded = { gesture ->
                            GestureRecordingManager.recordedGesture = gesture
                            sendBroadcast(Intent(ACTION_GESTURE_RECORDED).apply {
                                putExtra("selectedTrigger", selectedTrigger)
                            })
                            finishWithResult(existingX, existingY, null, keyCode, blockInput, singleAction, doubleAction)
                        },
                        onCancel = {
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun finishWithResult(
        x: Int,
        y: Int,
        gesture: List<SerializablePath>?,
        keyCode: Int,
        blockInput: Boolean,
        singleAction: String?,
        doubleAction: String?
    ) {
        cancelSetTargetNotification(this)
        sendBroadcast(Intent(ACTION_STOP_TARGETING))

        val encodedAppName = URLEncoder.encode(targetAppName, "UTF-8")
        var uriString = "tiktapremote://profile/${targetPackageName}/${encodedAppName}?x=$x&y=$y&selectedTrigger=$selectedTrigger&blockInput=$blockInput"
        if (keyCode != -1) uriString += "&keyCode=$keyCode"
        if (singleAction != null) uriString += "&singleAction=$singleAction"
        if (doubleAction != null) uriString += "&doubleAction=$doubleAction"

        val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(deepLinkIntent)
        finish()
    }
}