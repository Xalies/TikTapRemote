package com.xalies.tiktapremote

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.xalies.tiktapremote.service.TikTapAccessibilityService
import kotlinx.coroutines.flow.filter
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    appInfo: AppInfo,
    initialX: String?,
    initialY: String?,
    initialKeyCode: String?,
    initialBlockInput: Boolean?,
    initialShowVisualIndicator: Boolean?,
    initialActions: Map<TriggerType, Action>?,
    onBackClick: () -> Unit,
    initialSelectedTrigger: TriggerType = TriggerType.SINGLE_PRESS
) {
    val context = LocalContext.current
    val repository = remember { ProfileRepository(context) }

    var currentTier by remember { mutableStateOf(repository.getCurrentTier()) }
    fun refreshTier() { currentTier = repository.getCurrentTier() }

    val canConfigKey = repository.canConfigureTriggerKey()
    val canDoublePress = repository.canUseDoublePress()
    val canRepeat = repository.canUseRepeatMode()
    val showAds = repository.showAds()
    val defaultKey = remember { repository.getDefaultKeyCode() }

    var blockRemoteInput by remember { mutableStateOf(initialBlockInput ?: false) }
    var showKeycodeDialog by remember { mutableStateOf(false) }
    var showTrialDialog by remember { mutableStateOf(false) }
    var repeatInterval by remember { mutableStateOf(12000f) }

    var keyCode by remember(canConfigKey) {
        mutableStateOf(if (canConfigKey) (initialKeyCode?.toIntOrNull() ?: defaultKey) else defaultKey)
    }

    var tapTargetX by remember { mutableStateOf(initialX) }
    var tapTargetY by remember { mutableStateOf(initialY) }

    var assignedActions by remember { mutableStateOf(initialActions ?: emptyMap()) }
    var selectedTriggerForAssignment by remember { mutableStateOf(initialSelectedTrigger) }

    LaunchedEffect(canDoublePress) {
        if (!canDoublePress && selectedTriggerForAssignment == TriggerType.DOUBLE_PRESS) {
            selectedTriggerForAssignment = TriggerType.SINGLE_PRESS
        }
    }

    var timeRemaining by remember { mutableStateOf(repository.getTrialTimeRemaining()) }
    if (repository.isTrialActive()) {
        LaunchedEffect(Unit) {
            while(timeRemaining > 0) {
                kotlinx.coroutines.delay(1000)
                timeRemaining = repository.getTrialTimeRemaining()
                if (timeRemaining <= 0) {
                    refreshTier()
                    ProfileManager.checkAndEnforceLimits()
                }
            }
        }
    }

    val needsTouchTarget = assignedActions.values.any { it.type != ActionType.RECORDED }
    val isSavable = assignedActions.isNotEmpty() && (!needsTouchTarget || (tapTargetX != null && tapTargetY != null))

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_GESTURE_RECORDED) {
                    val receivedTrigger = intent.getStringExtra("selectedTrigger")
                    receivedTrigger?.let { triggerName ->
                        selectedTriggerForAssignment = TriggerType.valueOf(triggerName)
                    }
                    val recordedGesture = GestureRecordingManager.recordedGesture
                    if (recordedGesture != null) {
                        assignedActions = assignedActions.toMutableMap().apply {
                            this[selectedTriggerForAssignment] = Action(ActionType.RECORDED, recordedGesture)
                        }
                        GestureRecordingManager.recordedGesture = null
                    }
                }
            }
        }
        val intentFilter = IntentFilter(ACTION_GESTURE_RECORDED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, intentFilter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(Unit) {
        val recordedGesture = GestureRecordingManager.recordedGesture
        if (recordedGesture != null) {
            assignedActions = assignedActions.toMutableMap().apply {
                this[selectedTriggerForAssignment] = Action(ActionType.RECORDED, recordedGesture)
            }
            GestureRecordingManager.recordedGesture = null
            Toast.makeText(context, "Gesture Saved!", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchAppForAction(isRecording: Boolean) {
        val action = if (isRecording) ACTION_START_GESTURE_RECORDING else ACTION_START_TARGETING

        // Extract current actions
        val singleAction = assignedActions[TriggerType.SINGLE_PRESS]?.type?.name
        val doubleAction = assignedActions[TriggerType.DOUBLE_PRESS]?.type?.name

        val intent = Intent(action).apply {
            putExtra("packageName", appInfo.packageName)
            putExtra("appName", appInfo.name)
            putExtra("selectedTrigger", selectedTriggerForAssignment.name)
            putExtra("keyCode", keyCode)
            putExtra("blockInput", blockRemoteInput)
            putExtra("tapX", tapTargetX?.toIntOrNull() ?: 0)
            putExtra("tapY", tapTargetY?.toIntOrNull() ?: 0)

            // PASS ACTIONS HERE
            if (singleAction != null) putExtra("singleAction", singleAction)
            if (doubleAction != null) putExtra("doubleAction", doubleAction)
        }
        context.sendBroadcast(intent)
        val launchIntent = if (appInfo.packageName == GLOBAL_PROFILE_PACKAGE_NAME) {
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        } else {
            context.packageManager.getLaunchIntentForPackage(appInfo.packageName)
        }
        context.startActivity(launchIntent)

        // PASS ACTIONS HERE TOO
        if (isRecording) {
            showRecordingNotification(context, appInfo.packageName, appInfo.name, selectedTriggerForAssignment.name, singleAction, doubleAction)
        } else {
            showSetTargetNotification(context, appInfo.packageName, appInfo.name, keyCode, selectedTriggerForAssignment.name, singleAction, doubleAction)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> if (isGranted) launchAppForAction(false) }
    )

    if (showKeycodeDialog) {
        LaunchedEffect(Unit) {
            TikTapAccessibilityService.keyEvents.filter { it != KeyEvent.KEYCODE_BACK }.collect { receivedKeyCode ->
                keyCode = receivedKeyCode
                showKeycodeDialog = false
            }
        }
        KeycodeFinderDialog(onDismiss = { showKeycodeDialog = false })
    }

    if (showTrialDialog) {
        TrialOfferDialog(onDismiss = { showTrialDialog = false }, onAccept = {
            repository.activateTrial()
            refreshTier()
            showTrialDialog = false
            Toast.makeText(context, "10-Minute Trial Started!", Toast.LENGTH_LONG).show()
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile: ${appInfo.name}", fontSize = 20.sp) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = {
                        if (isSavable) {
                            val profile = Profile(
                                packageName = appInfo.packageName,
                                keyCode = keyCode,
                                tapX = tapTargetX?.toInt() ?: 0,
                                tapY = tapTargetY?.toInt() ?: 0,
                                blockInput = blockRemoteInput,
                                showVisualIndicator = false,
                                actions = assignedActions,
                                repeatInterval = repeatInterval.toLong()
                            )
                            ProfileManager.updateProfile(profile)
                            onBackClick()
                        }
                    }, enabled = isSavable) { Text("Save", fontWeight = FontWeight.Bold) }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (repository.isTrialActive()) {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(timeRemaining)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(timeRemaining) % 60
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Trial Active: %02d:%02d".format(minutes, seconds), color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                SettingItem(
                    title = "Input Trigger",
                    subtitle = if (!canConfigKey) "Volume Up (Locked)" else (keyCode.let { mapKeyCodeToString(it) }),
                    icon = Icons.Rounded.Settings,
                    isEnabled = canConfigKey,
                    onClick = { if(canConfigKey) showKeycodeDialog = true else if (!repository.hasUsedTrial()) showTrialDialog = true else Toast.makeText(context, "Requires Essentials", Toast.LENGTH_SHORT).show() }
                )
            }

            // Trigger Tabs
            Row(modifier = Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), verticalAlignment = Alignment.CenterVertically) {
                TriggerType.values().forEach { trigger ->
                    val isSelected = selectedTriggerForAssignment == trigger
                    val isLocked = trigger == TriggerType.DOUBLE_PRESS && !canDoublePress

                    val label = trigger.name.toFriendlyName()

                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(3.dp).clip(RoundedCornerShape(19.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { if(isLocked) { if (!repository.hasUsedTrial()) showTrialDialog = true else Toast.makeText(context, "Requires Essentials", Toast.LENGTH_SHORT).show() } else selectedTriggerForAssignment = trigger },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = if (isLocked) "$label 🔒" else label, style = MaterialTheme.typography.labelMedium, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else if (isLocked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // --- SECTION: TAPS & GESTURES ---
            // Left aligned header for taps
            Text("Taps & Gestures", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start).padding(top=8.dp))

            val tapActions = ActionType.values().filter { it.name.contains("TAP") || it == ActionType.RECORDED }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                tapActions.forEach { action ->
                    val isAssigned = assignedActions[selectedTriggerForAssignment]?.type == action
                    val isAllowed = repository.isActionAllowed(action)

                    // Renaming "Recorded" to "Record"
                    val label = if (action == ActionType.RECORDED) "Record" else action.name.toFriendlyName()

                    ActionIconItem(
                        label = label,
                        icon = getIconForAction(action),
                        isSelected = isAssigned,
                        isEnabled = isAllowed,
                        onClick = {
                            if (isAllowed) assignedActions = assignedActions.toMutableMap().apply { this[selectedTriggerForAssignment] = Action(action) }
                            else if (!repository.hasUsedTrial()) showTrialDialog = true
                            else Toast.makeText(context, "Locked Feature", Toast.LENGTH_SHORT).show()
                        }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }
            }

            // --- SECTION: SWIPES ---
            val swipeActions = ActionType.values().filter { it.name.contains("SWIPE") }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                swipeActions.forEachIndexed { index, action ->
                    val isAssigned = assignedActions[selectedTriggerForAssignment]?.type == action
                    val isAllowed = repository.isActionAllowed(action)

                    // Pass null label to hide text for swipes
                    ActionIconItem(
                        label = null,
                        icon = getIconForAction(action),
                        isSelected = isAssigned,
                        isEnabled = isAllowed,
                        onClick = {
                            if (isAllowed) assignedActions = assignedActions.toMutableMap().apply { this[selectedTriggerForAssignment] = Action(action) }
                            else if (!repository.hasUsedTrial()) showTrialDialog = true
                            else Toast.makeText(context, "Locked Feature", Toast.LENGTH_SHORT).show()
                        }
                    )
                    if (index < swipeActions.size - 1) Spacer(modifier = Modifier.width(16.dp))
                }
            }

            // Centered footer for swipes
            Text("Swipes", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            // Selected Action Detail View
            Spacer(modifier = Modifier.height(8.dp))
            val currentAction = assignedActions[selectedTriggerForAssignment]?.type
            val title = if(currentAction == ActionType.RECORDED) "Record Gesture" else "Set Point"
            val subtitle = if(currentAction == ActionType.RECORDED) "Press to start" else if (tapTargetX != null && tapTargetY != null) "X: $tapTargetX, Y: $tapTargetY" else "Not Set"

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                SettingItem(title = title, subtitle = subtitle, icon = if (currentAction == ActionType.RECORDED) Icons.Rounded.FiberManualRecord else Icons.Rounded.Place, onClick = {
                    val isRecording = currentAction == ActionType.RECORDED
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        when (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)) {
                            PackageManager.PERMISSION_GRANTED -> launchAppForAction(isRecording)
                            else -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else launchAppForAction(isRecording)
                })
            }

// Options & Repeat
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // --- NEW EXCLUSIVE MODE SWITCH ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon to show "No Volume"
                        Icon(
                            imageVector = if (blockRemoteInput) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                            contentDescription = null,
                            tint = if (blockRemoteInput) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Exclusive Mode", // Rebranded name
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Prevents volume changes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = blockRemoteInput,
                            onCheckedChange = { blockRemoteInput = it },
                            modifier = Modifier.scale(0.8f)
                        )
                    }

                    // Helper Tip (Only visible when enabled)
                    if (blockRemoteInput) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.NotificationsActive,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Tip: Tap 'Disable' in the notification to temporarily restore system interactions.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    // --------------------------------

                    if (canRepeat) {
                        Spacer(modifier = Modifier.height(12.dp)) // Add some spacing before divider
                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                        // ... existing repeat logic ...
                        val keyName = mapKeyCodeToString(keyCode)
                        Text("Repeat Interval (Hold $keyName 1s to Toggle)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top=8.dp))
                        Slider(value = repeatInterval, onValueChange = { repeatInterval = it }, valueRange = 3000f..30000f, steps = 26)
                        Text("${(repeatInterval / 1000).toInt()}s", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
                    }
                }
            }

            if (showAds) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AdMobBanner()
                    }
                }
            }
        }
    }
}

@Composable
fun TrialOfferDialog(onDismiss: () -> Unit, onAccept: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Rounded.Stars, null) }, title = { Text("Try Pro?") }, text = { Text("Unlock all features for 10 mins free!") }, confirmButton = { Button(onClick = onAccept) { Text("Start") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("No") } })
}

@Composable
fun SettingItem(title: String, subtitle: String, icon: ImageVector, isEnabled: Boolean = true, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp).alpha(if (isEnabled) 1f else 0.5f), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun ActionIconItem(
    label: String?,
    icon: ImageVector,
    isSelected: Boolean,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp).alpha(if (isEnabled) 1f else 0.5f)) {
        Card(onClick = onClick, enabled = true, modifier = Modifier.size(56.dp), colors = CardDefaults.cardColors(containerColor = containerColor), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, borderColor)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(26.dp), tint = contentColor)
            }
        }
        // Handle null label (for swipes)
        if (label != null) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.8f))
    }
}

@Composable
fun KeycodeFinderDialog(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Press Button") }, text = { Text("Press a remote button.") }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

fun String.toFriendlyName(): String {
    return this.lowercase().split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}

fun getIconForAction(actionType: ActionType): ImageVector {
    return when (actionType) {
        ActionType.SWIPE_UP -> Icons.Rounded.SwipeUp
        ActionType.SWIPE_DOWN -> Icons.Rounded.SwipeDown
        ActionType.SWIPE_LEFT -> Icons.Rounded.SwipeLeft
        ActionType.SWIPE_RIGHT -> Icons.Rounded.SwipeRight
        // Use same icon as Tap
        ActionType.DOUBLE_TAP -> Icons.Rounded.TouchApp
        ActionType.TAP -> Icons.Rounded.TouchApp
        ActionType.RECORDED -> Icons.Rounded.Gesture // thread_unread is not in the standard library yet
        else -> Icons.Rounded.SmartButton
    }
}

@Composable
fun AdMobBanner() {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                // Use the Test Ad Unit ID for now
                adUnitId = "ca-app-pub-3940256099942544/6300978111"
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    ProfileScreen(appInfo = AppInfo("Sample App", "com.sample.app", null), initialX = "100", initialY = "200", initialKeyCode = "24", initialBlockInput = false, initialShowVisualIndicator = false, initialActions = emptyMap(), onBackClick = {})
}