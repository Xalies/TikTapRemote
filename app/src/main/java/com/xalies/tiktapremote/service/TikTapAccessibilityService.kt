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
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
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
            val packageName = intent.getStringExtra("packageName")
            val selectedTrigger = intent.getStringExtra("selectedTrigger")

            val keyCode = intent.getIntExtra("keyCode", -1)
            val blockInput = intent.getBooleanExtra("blockInput", false)
            val tapX = intent.getIntExtra("tapX", 0)
            val tapY = intent.getIntExtra("tapY", 0)
            val singleAction = intent.getStringExtra("singleAction")
            val doubleAction = intent.getStringExtra("doubleAction")

            val overlayIntent = Intent(context, OverlayActivity::class.java).apply {
                putExtra("targetPackageName", packageName)
                putExtra("selectedTrigger", selectedTrigger)
                putExtra("keyCode", keyCode)
                putExtra("blockInput", blockInput)
                putExtra("tapX", tapX)
                putExtra("tapY", tapY)
                if (singleAction != null) putExtra("singleAction", singleAction)
                if (doubleAction != null) putExtra("doubleAction", doubleAction)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                if (isRepeatActive) {
                    isRepeatActive = false
                    repeatHandler.removeCallbacks(repeatRunnable)
                    Toast.makeText(this, "Repeat OFF (App Changed)", Toast.LENGTH_SHORT).show()
                }
            }

            currentPackageName = newPackage

            val hasAppProfile = profiles.any { it.packageName == currentPackageName.toString() }
            val hasGlobalProfile = profiles.any { it.packageName == GLOBAL_PROFILE_PACKAGE_NAME }

            if (hasAppProfile || hasGlobalProfile) {
                showControlNotification(this, repository.isServiceEnabled())
            } else {
                cancelControlNotification(this)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onCreate() {
        super.onCreate()
        ProfileManager.initialize(this)
        repository = ProfileRepository(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

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
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (!repository.isServiceEnabled()) return super.onKeyEvent(event)
        if (event?.action == KeyEvent.ACTION_DOWN) serviceScope.launch { _keyEvents.emit(event.keyCode) }

        if (event?.keyCode == KeyEvent.KEYCODE_BACK || event?.keyCode == KeyEvent.KEYCODE_HOME || event?.keyCode == KeyEvent.KEYCODE_APP_SWITCH) return super.onKeyEvent(event)
        if (currentPackageName?.toString() == packageName) return true

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
        } else {
            repeatHandler.removeCallbacks(repeatRunnable)
            Toast.makeText(this, "Repeat OFF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performActionForProfile(profile: Profile, action: Action) {
        if (!repository.isActionAllowed(action.type)) return
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