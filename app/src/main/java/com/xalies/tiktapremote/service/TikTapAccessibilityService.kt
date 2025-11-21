package com.xalies.tiktapremote.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.xalies.tiktapremote.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.random.Random

class TikTapAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentPackageName: CharSequence? = null
    private lateinit var repository: ProfileRepository
    private lateinit var audioManager: AudioManager
    private lateinit var vibrator: Vibrator

    // Overlay Manager
    private lateinit var overlayManager: OverlayManager

    private var profiles: Set<Profile> = setOf()
    private var targetPackageForOverlay: String? = null

    private val doublePressHandler = Handler(Looper.getMainLooper())
    private var lastKeyPressed: Int? = null
    private var isDoublePressPending = false
    private val DOUBLE_PRESS_TIMEOUT = 400L

    private val longPressHandler = Handler(Looper.getMainLooper())
    private var isLongPressTriggered = false
    private val LONG_PRESS_TIMEOUT = 1000L

    private var isRepeatActive = false
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var currentProfileInterval = 12000L

    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (!isRepeatActive) return
            val profile = getCurrentProfile(lastKeyPressed ?: return)
            if (profile != null && profile.isEnabled) {
                val action = profile.actions[TriggerType.SINGLE_PRESS]
                if (action != null) performActionForProfile(profile, action)
            }
            val randomDelay = Random.nextLong(-1000, 1000)
            repeatHandler.postDelayed(this, currentProfileInterval + randomDelay)
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.getStringExtra("packageName") ?: ""
            val appName = intent.getStringExtra("appName") ?: ""
            val selectedTrigger = intent.getStringExtra("selectedTrigger") ?: "SINGLE_PRESS"

            val keyCode = intent.getIntExtra("keyCode", -1)
            val blockInput = intent.getBooleanExtra("blockInput", false)
            val tapX = intent.getIntExtra("tapX", 0)
            val tapY = intent.getIntExtra("tapY", 0)
            val singleAction = intent.getStringExtra("singleAction")
            val doubleAction = intent.getStringExtra("doubleAction")

            when (intent.action) {
                ACTION_START_TARGETING -> {
                    targetPackageForOverlay = packageName
                    overlayManager.showOverlay(
                        mode = "targeting",
                        targetPackageName = packageName,
                        targetAppName = appName,
                        selectedTrigger = selectedTrigger,
                        keyCode = keyCode,
                        blockInput = blockInput,
                        existingX = tapX,
                        existingY = tapY,
                        singleAction = singleAction,
                        doubleAction = doubleAction
                    )
                }
                ACTION_START_GESTURE_RECORDING -> {
                    targetPackageForOverlay = packageName
                    overlayManager.showOverlay(
                        mode = "recording",
                        targetPackageName = packageName,
                        targetAppName = appName,
                        selectedTrigger = selectedTrigger,
                        keyCode = keyCode,
                        blockInput = blockInput,
                        existingX = tapX,
                        existingY = tapY,
                        singleAction = singleAction,
                        doubleAction = doubleAction
                    )
                }
                ACTION_STOP_TARGETING -> {
                    targetPackageForOverlay = null
                    overlayManager.removeOverlay()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.packageName != null) {
            val newPackage = event.packageName
            if (newPackage.startsWith("com.android.systemui")) return

            // Basic check - we'll do a deeper check on key press
            if (currentPackageName != newPackage) {
                currentPackageName = newPackage
                if (isRepeatActive) {
                    isRepeatActive = false
                    repeatHandler.removeCallbacks(repeatRunnable)
                    Toast.makeText(this, "Repeat OFF (App Changed)", Toast.LENGTH_SHORT).show()
                }
                updateNotificationState()
            }
        }
    }

    private fun updateNotificationState() {
        val hasAppProfile = profiles.any { it.packageName == currentPackageName.toString() }
        val hasGlobalProfile = profiles.any { it.packageName == GLOBAL_PROFILE_PACKAGE_NAME }

        if (hasAppProfile || hasGlobalProfile) {
            showControlNotification(this, repository.isServiceEnabled())
        } else {
            cancelControlNotification(this)
        }
    }

    override fun onInterrupt() {}

    override fun onCreate() {
        super.onCreate()
        ProfileManager.initialize(this)
        repository = ProfileRepository(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        overlayManager = OverlayManager(this)

        ProfileManager.profiles
            .onEach { updatedProfiles -> profiles = updatedProfiles }
            .launchIn(serviceScope)

        val intentFilter = IntentFilter().apply {
            addAction(ACTION_START_TARGETING)
            addAction(ACTION_STOP_TARGETING)
            addAction(ACTION_START_GESTURE_RECORDING)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, intentFilter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
        repeatHandler.removeCallbacks(repeatRunnable)
        overlayManager.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (!repository.isServiceEnabled()) return super.onKeyEvent(event)

        // 1. Aggressively try to find the real active package
        detectActivePackage()

        if (event?.action == KeyEvent.ACTION_DOWN) serviceScope.launch { _keyEvents.emit(event.keyCode) }

        if (event?.keyCode == KeyEvent.KEYCODE_BACK || event?.keyCode == KeyEvent.KEYCODE_HOME || event?.keyCode == KeyEvent.KEYCODE_APP_SWITCH) return super.onKeyEvent(event)

        // If we are somehow in the TikTap app itself, let keys pass normally
        if (currentPackageName?.toString() == packageName) return true

        val keyCode = event!!.keyCode
        val action = event.action
        val profile = getCurrentProfile(keyCode) ?: run {
            // No profile found for this app (and Global didn't match) -> Pass through
            if (action == KeyEvent.ACTION_DOWN) passThroughKeyEvent(keyCode)
            return true
        }

        if (!profile.isEnabled) return super.onKeyEvent(event)

        if (action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0) {
                isLongPressTriggered = false
                longPressHandler.postDelayed({
                    isLongPressTriggered = true
                    toggleRepeatMode(profile)
                }, LONG_PRESS_TIMEOUT)
            }
            return true
        }
        else if (action == KeyEvent.ACTION_UP) {
            longPressHandler.removeCallbacksAndMessages(null)
            if (isLongPressTriggered) {
                isLongPressTriggered = false
                return true
            }
            if (isRepeatActive) {
                profile.actions[TriggerType.SINGLE_PRESS]?.let { performActionForProfile(profile, it) }
                repeatHandler.removeCallbacks(repeatRunnable)
                repeatHandler.postDelayed(repeatRunnable, currentProfileInterval)
                return true
            }
            if (isDoublePressPending && keyCode == lastKeyPressed) {
                isDoublePressPending = false
                doublePressHandler.removeCallbacksAndMessages(null)
                profile.actions[TriggerType.DOUBLE_PRESS]?.let { performActionForProfile(profile, it) }
            } else {
                isDoublePressPending = true
                lastKeyPressed = keyCode
                doublePressHandler.postDelayed({
                    if (isDoublePressPending) {
                        profile.actions[TriggerType.SINGLE_PRESS]?.let { performActionForProfile(profile, it) }
                        isDoublePressPending = false
                    }
                }, DOUBLE_PRESS_TIMEOUT)
            }
            return true
        }
        return super.onKeyEvent(event)
    }

    /**
     * Robustly detects the top package, even in fullscreen games/overlays.
     * This fixes the issue where "rootInActiveWindow" returns null.
     */
    private fun detectActivePackage() {
        var detectedPackage: CharSequence? = null

        // Method 1: Standard rootInActiveWindow (Fastest)
        try {
            val root = rootInActiveWindow
            if (root?.packageName != null) {
                detectedPackage = root.packageName
            }
        } catch (e: Exception) {
            // Ignore
        }

        // Method 2: Window List Iteration (Most Robust)
        // Requires flagRetrieveInteractiveWindows in xml config
        if (detectedPackage == null || detectedPackage.toString().startsWith("com.android.systemui")) {
            try {
                val windowList = windows
                // Iterate from front (0) to back
                for (window in windowList) {
                    // We want the top-most window that is active/focused and NOT system UI
                    if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        if (window.root?.packageName != null) {
                            val pkg = window.root.packageName
                            if (!pkg.toString().startsWith("com.android.systemui")) {
                                detectedPackage = pkg
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // Update state if we found something new
        if (detectedPackage != null && detectedPackage != currentPackageName) {
            currentPackageName = detectedPackage
            updateNotificationState()
        }
    }

    private fun getCurrentProfile(keyCode: Int): Profile? {
        val hasAppSpecificProfile = profiles.any { it.packageName == currentPackageName }
        return if (hasAppSpecificProfile) {
            profiles.find { it.keyCode == keyCode && it.packageName == currentPackageName }
        } else {
            profiles.find { it.keyCode == keyCode && it.packageName == GLOBAL_PROFILE_PACKAGE_NAME }
        }
    }

    private fun toggleRepeatMode(profile: Profile) {
        if (!repository.canUseRepeatMode()) return
        isRepeatActive = !isRepeatActive
        if (isRepeatActive) {
            currentProfileInterval = profile.repeatInterval
            lastKeyPressed = profile.keyCode
            repeatHandler.post(repeatRunnable)
            Toast.makeText(this, "Repeat ON", Toast.LENGTH_SHORT).show()
            vibrate()
        } else {
            repeatHandler.removeCallbacks(repeatRunnable)
            Toast.makeText(this, "Repeat OFF", Toast.LENGTH_SHORT).show()
            vibrate()
        }
    }

    private fun performActionForProfile(profile: Profile, action: Action) {
        if (!repository.isActionAllowed(action.type)) return

        if (repository.isHapticEnabled()) {
            vibrate()
        }

        when (action.type) {
            ActionType.TAP -> performTap(profile.tapX, profile.tapY)
            ActionType.DOUBLE_TAP -> performDoubleTap(profile.tapX, profile.tapY)
            ActionType.SWIPE_UP -> performSwipe(profile.tapX, profile.tapY, 0f, -500f)
            ActionType.SWIPE_DOWN -> performSwipe(profile.tapX, profile.tapY, 0f, 500f)
            ActionType.SWIPE_LEFT -> performSwipe(profile.tapX, profile.tapY, -500f, 0f)
            ActionType.SWIPE_RIGHT -> performSwipe(profile.tapX, profile.tapY, 500f, 0f)
            ActionType.RECORDED -> action.recordedGesture?.let { performRecordedGesture(it) }
            else -> {}
        }
        if (!profile.blockInput) passThroughKeyEvent(profile.keyCode)
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    private fun performRecordedGesture(serializablePaths: List<SerializablePath>) {
        if (serializablePaths.isEmpty()) return
        val gestureBuilder = GestureDescription.Builder()
        val pathsToPlay = serializablePaths.take(10)

        for (serializablePath in pathsToPlay) {
            if (serializablePath.points.size > 1) {
                val path = Path()
                path.moveTo(serializablePath.points.first().x, serializablePath.points.first().y)
                for (i in 1 until serializablePath.points.size) {
                    val point = serializablePath.points[i]
                    path.lineTo(point.x, point.y)
                }
                val strokeDuration = serializablePath.duration.coerceIn(50L, 10000L)
                val strokeDelay = serializablePath.delay

                try {
                    gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, strokeDelay, strokeDuration))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        try { dispatchGesture(gestureBuilder.build(), null, null) } catch (e: Exception) { e.printStackTrace() }
    }

    private fun passThroughKeyEvent(keyCode: Int) {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            KeyEvent.KEYCODE_VOLUME_DOWN -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        }
    }

    private fun performTap(x: Int, y: Int) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 100)).build()
        dispatchGesture(gesture, null, null)
    }

    private fun performDoubleTap(x: Int, y: Int) {
        val tapPath = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(tapPath, 0, 50))
            .addStroke(GestureDescription.StrokeDescription(tapPath, 150, 50))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun performSwipe(x: Int, y: Int, deltaX: Float, deltaY: Float) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()); lineTo(x + deltaX, y + deltaY) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 250)).build()
        dispatchGesture(gesture, null, null)
    }

    companion object {
        val _keyEvents = MutableSharedFlow<Int>()
        val keyEvents = _keyEvents.asSharedFlow()
    }
}