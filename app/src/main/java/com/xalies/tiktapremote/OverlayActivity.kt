package com.xalies.tiktapremote

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.xalies.tiktapremote.ui.theme.TikTapRemoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

        setContent {
            TikTapRemoteTheme {
                if (mode == "targeting") {
                    OverlayView(
                        mode = "targeting",
                        onConfirmTarget = { x, y ->
                            saveAndFinish(x, y, null, keyCode, blockInput)
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
                            saveAndFinish(0, 0, gesture, keyCode, blockInput)
                        },
                        onCancel = {
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun saveAndFinish(
        x: Int,
        y: Int,
        gesture: List<SerializablePath>?,
        keyCode: Int,
        blockInput: Boolean
    ) {
        cancelSetTargetNotification(this)
        sendBroadcast(Intent(ACTION_STOP_TARGETING))

        // *** SAVE TO DB BEFORE NAVIGATING ***
        lifecycleScope.launch(Dispatchers.IO) {
            val repo = ProfileRepository(applicationContext)
            val profile = repo.getProfileByPackage(targetPackageName)

            if (profile != null) {
                val triggerType = try { TriggerType.valueOf(selectedTrigger) } catch (e: Exception) { TriggerType.SINGLE_PRESS }
                val newActions = profile.actions.toMutableMap()

                val currentAction = newActions[triggerType] ?: Action(ActionType.TAP)
                val updatedAction = if (gesture != null) {
                    currentAction.copy(type = ActionType.RECORDED, recordedGesture = gesture)
                } else {
                    // For targeting, ensure we have a coordinate-based type (default TAP)
                    val type = if (currentAction.type == ActionType.RECORDED) ActionType.TAP else currentAction.type
                    currentAction.copy(type = type, tapX = x, tapY = y)
                }

                newActions[triggerType] = updatedAction
                repo.saveProfile(profile.copy(actions = newActions))
            }

            // *** NAVIGATE BACK ***
            withContext(Dispatchers.Main) {
                val encodedAppName = URLEncoder.encode(targetAppName, "UTF-8")
                // We pass selectedTrigger so the profile screen knows which tab to open
                val uriString = "tiktapremote://profile/${targetPackageName}/${encodedAppName}?selectedTrigger=$selectedTrigger"

                val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(deepLinkIntent)
                finish()
            }
        }
    }
}