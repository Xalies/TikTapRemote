package com.xalies.tiktapremote

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.xalies.tiktapremote.ui.theme.TikTapRemoteTheme
import java.net.URLEncoder

class OverlayActivity : ComponentActivity() {

    private var targetPackageName = ""
    private var targetAppName = ""
    private var selectedTrigger = ""

    // Hold state to pass back
    private var singleActionType: String? = null
    private var doubleActionType: String? = null
    private var singleX = 0
    private var singleY = 0
    private var doubleX = 0
    private var doubleY = 0

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

        // Read all state to preserve it
        if (intent.hasExtra("singleAction")) singleActionType = intent.getStringExtra("singleAction")
        if (intent.hasExtra("doubleAction")) doubleActionType = intent.getStringExtra("doubleAction")

        singleX = intent.getIntExtra("singleX", 0)
        singleY = intent.getIntExtra("singleY", 0)
        doubleX = intent.getIntExtra("doubleX", 0)
        doubleY = intent.getIntExtra("doubleY", 0)

        setContent {
            TikTapRemoteTheme {
                if (mode == "targeting") {
                    OverlayView(
                        mode = "targeting",
                        onConfirmTarget = { x, y ->
                            // Update the specific trigger's coordinates
                            if (selectedTrigger == TriggerType.SINGLE_PRESS.name) {
                                singleX = x
                                singleY = y
                            } else if (selectedTrigger == TriggerType.DOUBLE_PRESS.name) {
                                doubleX = x
                                doubleY = y
                            }
                            finishWithResult(keyCode, blockInput)
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
                            finishWithResult(keyCode, blockInput)
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
        keyCode: Int,
        blockInput: Boolean
    ) {
        cancelSetTargetNotification(this)
        sendBroadcast(Intent(ACTION_STOP_TARGETING))

        val encodedAppName = URLEncoder.encode(targetAppName, "UTF-8")

        // Build URI with ALL state
        var uriString = "tiktapremote://profile/${targetPackageName}/${encodedAppName}?selectedTrigger=$selectedTrigger&blockInput=$blockInput"

        if (keyCode != -1) uriString += "&keyCode=$keyCode"

        // Always pass X/Y
        uriString += "&singleX=$singleX&singleY=$singleY&doubleX=$doubleX&doubleY=$doubleY"

        // Only pass types if they exist
        if (singleActionType != null) uriString += "&singleAction=$singleActionType"
        if (doubleActionType != null) uriString += "&doubleAction=$doubleActionType"

        val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(deepLinkIntent)
        finish()
    }
}