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
import android.os.VibratorManager // Added
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
    private lateinit var overlayManager: OverlayManager

    private var profiles: Set<Profile> = setOf()
    private var targetPackageForOverlay: String? = null

    private val doublePressHandler = Handler(Looper.getMainLooper())
    private var lastKeyPressed: Int? = null
    private var isDoublePressPending = false
    private val DOUBLE_PRESS_TIMEOUT = 400L

    private var lastTriggerType: TriggerType = TriggerType.SINGLE_PRESS

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
                val action = profile.actions[lastTriggerType]
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
            val singleAction = intent.getStringExtra("singleAction")
            val doubleAction = intent.getStringExtra("doubleAction")
            val singleX = intent.getIntExtra("singleX", 0)
            val singleY = intent.getIntExtra("singleY", 0)
            val doubleX = intent.getIntExtra("doubleX", 0)
            val doubleY = intent.getIntExtra("doubleY", 0)

            val overlayIntent = Intent(context, com.xalies.tiktapremote.OverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("targetPackageName", packageName)
                putExtra("targetAppName", appName)
                putExtra("selectedTrigger", selectedTrigger)
                putExtra("keyCode", keyCode)
                putExtra("blockInput", blockInput)
                putExtra("singleAction", singleAction)
                putExtra("doubleAction", doubleAction)
                putExtra("singleX", singleX)
                putExtra("singleY", singleY)
                putExtra("doubleX", doubleX)
                putExtra("doubleY", doubleY)
            }

            when (intent.action) {
                ACTION_START_TARGETING -> {
                    targetPackageForOverlay = packageName
                    overlayIntent.putExtra("mode", "targeting")
                    context.startActivity(overlayIntent)
                }
                ACTION_START_GESTURE_RECORDING -> {
                    targetPackageForOverlay = packageName
                    overlayIntent.putExtra("mode", "recording")
                    context.startActivity(overlayIntent)
                }
                ACTION_STOP_TARGETING -> {
                    targetPackageForOverlay = null
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

        // FIXED: Use VibratorManager for Android 12+
        val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibrator = vibratorManager.defaultVibrator

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
        detectActivePackage()
        if (event?.action == KeyEvent.ACTION_DOWN) serviceScope.launch { _keyEvents.emit(event.keyCode) }

        if (event?.keyCode == KeyEvent.KEYCODE_BACK || event?.keyCode == KeyEvent.KEYCODE_HOME || event?.keyCode == KeyEvent.KEYCODE_APP_SWITCH) return super.onKeyEvent(event)

        // FIXED: Allow events to pass through normally when inside the TikTap app
        if (currentPackageName?.toString() == packageName) return super.onKeyEvent(event)

        val keyCode = event!!.keyCode
        val action = event.action
        val profile = getCurrentProfile(keyCode) ?: run {
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
                repeatHandler.removeCallbacks(repeatRunnable)
                repeatHandler.postDelayed(repeatRunnable, currentProfileInterval)
                return true
            }
            if (isDoublePressPending && keyCode == lastKeyPressed) {
                isDoublePressPending = false
                doublePressHandler.removeCallbacksAndMessages(null)
                lastTriggerType = TriggerType.DOUBLE_PRESS
                profile.actions[TriggerType.DOUBLE_PRESS]?.let { performActionForProfile(profile, it) }
            } else {
                isDoublePressPending = true
                lastKeyPressed = keyCode
                doublePressHandler.postDelayed({
                    if (isDoublePressPending) {
                        lastTriggerType = TriggerType.SINGLE_PRESS
                        profile.actions[TriggerType.SINGLE_PRESS]?.let { performActionForProfile(profile, it) }
                        isDoublePressPending = false
                    }
                }, DOUBLE_PRESS_TIMEOUT)
            }
            return true
        }
        return super.onKeyEvent(event)
    }

    private fun detectActivePackage() {
        var detectedPackage: CharSequence? = null
        try {
            val root = rootInActiveWindow
            if (root?.packageName != null) {
                detectedPackage = root.packageName
            }
        } catch (e: Exception) {}

        if (detectedPackage == null || detectedPackage.toString().startsWith("com.android.systemui")) {
            try {
                val windowList = windows
                for (window in windowList) {
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
            } catch (e: Exception) {}
        }

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
        if (repository.isHapticEnabled()) vibrate()

        val x = if (action.tapX != 0) action.tapX else profile.tapX
        val y = if (action.tapY != 0) action.tapY else profile.tapY

        when (action.type) {
            ActionType.TAP -> performTap(x, y)
            ActionType.DOUBLE_TAP -> performDoubleTap(x, y)
            ActionType.SWIPE_UP -> performSwipe(x, y, 0f, -500f)
            ActionType.SWIPE_DOWN -> performSwipe(x, y, 0f, 500f)
            ActionType.SWIPE_LEFT -> performSwipe(x, y, -500f, 0f)
            ActionType.SWIPE_RIGHT -> performSwipe(x, y, 500f, 0f)
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
        var hasValidStroke = false

        for (serializablePath in pathsToPlay) {
            if (serializablePath.points.isNotEmpty()) {
                val path = Path()
                val startX = serializablePath.points.first().x
                val startY = serializablePath.points.first().y
                path.moveTo(startX, startY)
                if (serializablePath.points.size == 1) {
                    path.lineTo(startX, startY)
                } else {
                    for (i in 1 until serializablePath.points.size) {
                        val point = serializablePath.points[i]
                        path.lineTo(point.x, point.y)
                    }
                }
                val strokeDuration = serializablePath.duration.coerceIn(50L, 10000L)
                val strokeDelay = serializablePath.delay
                try {
                    gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, strokeDelay, strokeDuration))
                    hasValidStroke = true
                } catch (e: Exception) {}
            }
        }
        if (hasValidStroke) {
            try { dispatchGesture(gestureBuilder.build(), null, null) } catch (e: Exception) {}
        }
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