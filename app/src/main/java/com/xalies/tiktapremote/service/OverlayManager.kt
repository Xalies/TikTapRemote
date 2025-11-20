package com.xalies.tiktapremote.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.xalies.tiktapremote.ACTION_GESTURE_RECORDED
import com.xalies.tiktapremote.ACTION_STOP_TARGETING
import com.xalies.tiktapremote.GestureRecordingManager
import com.xalies.tiktapremote.OverlayView
import com.xalies.tiktapremote.SerializablePath
import com.xalies.tiktapremote.cancelSetTargetNotification
import com.xalies.tiktapremote.ui.theme.TikTapRemoteTheme
import java.net.URLEncoder

class OverlayManager(private val context: Context) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: ComposeView? = null

    // Lifecycle management for Compose
    private val lifecycleRegistry = LifecycleRegistry(this)

    // Fix: Declare viewModelStore once as an override
    override val viewModelStore = ViewModelStore()

    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

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
        removeOverlay() // Safety check to ensure we don't double add

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, // Key flag for drawing over everything
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        overlayView = ComposeView(context).apply {
            // Required for Compose to work in a Service/Window context
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(this@OverlayManager)
            setViewTreeViewModelStoreOwner(this@OverlayManager)
            setViewTreeSavedStateRegistryOwner(this@OverlayManager)

            setContent {
                TikTapRemoteTheme {
                    if (mode == "targeting") {
                        OverlayView(
                            mode = "targeting",
                            onConfirmTarget = { x, y ->
                                finishWithResult(
                                    targetPackageName, targetAppName, selectedTrigger,
                                    x, y, null, keyCode, blockInput, singleAction, doubleAction
                                )
                            },
                            onCancel = {
                                closeOverlay()
                            }
                        )
                    } else {
                        OverlayView(
                            mode = "recording",
                            onGestureRecorded = { gesture ->
                                GestureRecordingManager.recordedGesture = gesture
                                context.sendBroadcast(Intent(ACTION_GESTURE_RECORDED).apply {
                                    putExtra("selectedTrigger", selectedTrigger)
                                })
                                finishWithResult(
                                    targetPackageName, targetAppName, selectedTrigger,
                                    existingX, existingY, null, keyCode, blockInput, singleAction, doubleAction
                                )
                            },
                            onCancel = {
                                closeOverlay()
                            }
                        )
                    }
                }
            }
        }

        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun closeOverlay() {
        cancelSetTargetNotification(context)
        context.sendBroadcast(Intent(ACTION_STOP_TARGETING))
        removeOverlay()
    }

    fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
    }

    private fun finishWithResult(
        targetPackageName: String,
        targetAppName: String,
        selectedTrigger: String,
        x: Int,
        y: Int,
        gesture: List<SerializablePath>?,
        keyCode: Int,
        blockInput: Boolean,
        singleAction: String?,
        doubleAction: String?
    ) {
        closeOverlay()

        val encodedAppName = URLEncoder.encode(targetAppName, "UTF-8")
        var uriString = "tiktapremote://profile/${targetPackageName}/${encodedAppName}?x=$x&y=$y&selectedTrigger=$selectedTrigger&blockInput=$blockInput"
        if (keyCode != -1) uriString += "&keyCode=$keyCode"
        if (singleAction != null) uriString += "&singleAction=$singleAction"
        if (doubleAction != null) uriString += "&doubleAction=$doubleAction"

        val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(deepLinkIntent)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}