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
    initialKeyCode: String?,
    initialBlockInput: Boolean?,
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

    // Initialize assignedActions from params
    var assignedActions by remember { mutableStateOf(initialActions ?: emptyMap()) }
    var selectedTriggerForAssignment by remember { mutableStateOf(initialSelectedTrigger) }

    // Update local state if params change (Deep Link return)
    LaunchedEffect(initialActions) {
        if (initialActions != null) {
            assignedActions = initialActions
        }
    }

    LaunchedEffect(initialSelectedTrigger) {
        selectedTriggerForAssignment = initialSelectedTrigger
    }

    val activeAction = assignedActions[selectedTriggerForAssignment]
    val tapTargetX = activeAction?.tapX
    val tapTargetY = activeAction?.tapY

    LaunchedEffect(canDoublePress) {
        if (!canDoublePress && selectedTriggerForAssignment == TriggerType.DOUBLE_PRESS) {
            selectedTriggerForAssignment = TriggerType.SINGLE_PRESS
        }
    }

    var timeRemaining by remember { mutableStateOf(repository.getTrialTimeRemaining()) }
    if (repository.isTrialActive() || repository.isAdRewardActive()) {
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

    val isSavable = assignedActions.isNotEmpty()

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_GESTURE_RECORDED) {
                    val receivedTrigger = intent.getStringExtra("selectedTrigger")
                    val triggerToUpdate = if (receivedTrigger != null) TriggerType.valueOf(receivedTrigger) else selectedTriggerForAssignment

                    val recordedGesture = GestureRecordingManager.recordedGesture
                    if (recordedGesture != null) {
                        assignedActions = assignedActions.toMutableMap().apply {
                            val existingAction = this[triggerToUpdate]
                            val x = existingAction?.tapX ?: 0
                            val y = existingAction?.tapY ?: 0
                            this[triggerToUpdate] = Action(ActionType.RECORDED, recordedGesture, tapX = x, tapY = y)
                        }
                        GestureRecordingManager.recordedGesture = null
                        selectedTriggerForAssignment = triggerToUpdate
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

    fun launchAppForAction(isRecording: Boolean) {
        val action = if (isRecording) ACTION_START_GESTURE_RECORDING else ACTION_START_TARGETING

        val singleAction = assignedActions[TriggerType.SINGLE_PRESS]
        val doubleAction = assignedActions[TriggerType.DOUBLE_PRESS]

        val intent = Intent(action).apply {
            putExtra("packageName", appInfo.packageName)
            putExtra("appName", appInfo.name)
            putExtra("selectedTrigger", selectedTriggerForAssignment.name)
            putExtra("keyCode", keyCode)
            putExtra("blockInput", blockRemoteInput)

            putExtra("singleX", singleAction?.tapX ?: 0)
            putExtra("singleY", singleAction?.tapY ?: 0)
            putExtra("doubleX", doubleAction?.tapX ?: 0)
            putExtra("doubleY", doubleAction?.tapY ?: 0)

            if (singleAction != null) putExtra("singleAction", singleAction.type.name)
            if (doubleAction != null) putExtra("doubleAction", doubleAction.type.name)
        }
        context.sendBroadcast(intent)
        val launchIntent = if (appInfo.packageName == GLOBAL_PROFILE_PACKAGE_NAME) {
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        } else {
            context.packageManager.getLaunchIntentForPackage(appInfo.packageName)
        }
        context.startActivity(launchIntent)

        if (isRecording) {
            showRecordingNotification(context, appInfo.packageName, appInfo.name, selectedTriggerForAssignment.name, singleAction?.type?.name, doubleAction?.type?.name)
        } else {
            showSetTargetNotification(context, appInfo.packageName, appInfo.name, keyCode, selectedTriggerForAssignment.name, singleAction?.type?.name, doubleAction?.type?.name)
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
        AlertDialog(
            onDismissRequest = { showTrialDialog = false },
            title = { Text("Feature Locked") },
            text = { Text("This feature requires Pro. Please go to the App List (+) to activate a trial or upgrade.") },
            confirmButton = { TextButton(onClick = { showTrialDialog = false }) { Text("OK") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile: ${appInfo.name}", fontSize = 20.sp) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = {
                        if (isSavable) {
                            val fallbackX = assignedActions[TriggerType.SINGLE_PRESS]?.tapX ?: 0
                            val fallbackY = assignedActions[TriggerType.SINGLE_PRESS]?.tapY ?: 0

                            val profile = Profile(
                                packageName = appInfo.packageName,
                                keyCode = keyCode,
                                tapX = fallbackX,
                                tapY = fallbackY,
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
            if (repository.isTrialActive() || repository.isAdRewardActive()) {
                val hours = TimeUnit.MILLISECONDS.toHours(timeRemaining)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(timeRemaining) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(timeRemaining) % 60
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Trial Active: %02d:%02d:%02d".format(hours, minutes, seconds), color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                SettingItem(
                    title = "Input Trigger",
                    subtitle = if (!canConfigKey) "Volume Up (Locked)" else (keyCode.let { mapKeyCodeToString(it) }),
                    icon = Icons.Rounded.Settings,
                    isEnabled = canConfigKey,
                    onClick = { if(canConfigKey) showKeycodeDialog = true else showTrialDialog = true }
                )
            }

            Row(modifier = Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), verticalAlignment = Alignment.CenterVertically) {
                TriggerType.values().forEach { trigger ->
                    val isSelected = selectedTriggerForAssignment == trigger
                    val isLocked = trigger == TriggerType.DOUBLE_PRESS && !canDoublePress

                    val label = trigger.name.toFriendlyName()

                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(3.dp).clip(RoundedCornerShape(19.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { if(isLocked) { showTrialDialog = true } else selectedTriggerForAssignment = trigger },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = if (isLocked) "$label 🔒" else label, style = MaterialTheme.typography.labelMedium, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else if (isLocked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Text("Taps & Gestures", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start).padding(top=8.dp))

            val tapActions = ActionType.values().filter { it.name.contains("TAP") || it == ActionType.RECORDED }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                tapActions.forEach { action ->
                    val isAssigned = assignedActions[selectedTriggerForAssignment]?.type == action
                    val isAllowed = repository.isActionAllowed(action)

                    val label = if (action == ActionType.RECORDED) "Record" else action.name.toFriendlyName()

                    ActionIconItem(
                        label = label,
                        icon = getIconForAction(action),
                        isSelected = isAssigned,
                        isEnabled = isAllowed,
                        onClick = {
                            if (isAllowed) {
                                if (isAssigned) {
                                    assignedActions = assignedActions.toMutableMap().apply { remove(selectedTriggerForAssignment) }
                                } else {
                                    val currentX = assignedActions[selectedTriggerForAssignment]?.tapX ?: 0
                                    val currentY = assignedActions[selectedTriggerForAssignment]?.tapY ?: 0

                                    val newAction = Action(
                                        type = action,
                                        tapX = if (action != ActionType.RECORDED) currentX else 0,
                                        tapY = if (action != ActionType.RECORDED) currentY else 0
                                    )
                                    assignedActions = assignedActions.toMutableMap().apply { this[selectedTriggerForAssignment] = newAction }
                                }
                            }
                            else showTrialDialog = true
                        }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }
            }

            val swipeActions = ActionType.values().filter { it.name.contains("SWIPE") }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                swipeActions.forEachIndexed { index, action ->
                    val isAssigned = assignedActions[selectedTriggerForAssignment]?.type == action
                    val isAllowed = repository.isActionAllowed(action)

                    ActionIconItem(
                        label = null,
                        icon = getIconForAction(action),
                        isSelected = isAssigned,
                        isEnabled = isAllowed,
                        onClick = {
                            if (isAllowed) {
                                if (isAssigned) {
                                    assignedActions = assignedActions.toMutableMap().apply { remove(selectedTriggerForAssignment) }
                                } else {
                                    val currentX = assignedActions[selectedTriggerForAssignment]?.tapX ?: 0
                                    val currentY = assignedActions[selectedTriggerForAssignment]?.tapY ?: 0
                                    val newAction = Action(type = action, tapX = currentX, tapY = currentY)
                                    assignedActions = assignedActions.toMutableMap().apply { this[selectedTriggerForAssignment] = newAction }
                                }
                            }
                            else showTrialDialog = true
                        }
                    )
                    if (index < swipeActions.size - 1) Spacer(modifier = Modifier.width(16.dp))
                }
            }

            Text("Swipes", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(8.dp))
            val currentAction = assignedActions[selectedTriggerForAssignment]
            val type = currentAction?.type

            val isActionSelected = type != null
            val isRecordedType = type == ActionType.RECORDED

            val title = if(isRecordedType) "Record Gesture" else "Set Point"
            val subtitle = if (!isActionSelected) "Select an action above"
            else if(isRecordedType) "Press to start"
            else if (currentAction?.tapX != 0 && currentAction?.tapY != 0) "X: ${currentAction?.tapX}, Y: ${currentAction?.tapY}"
            else "Not Set"

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                SettingItem(
                    title = title,
                    subtitle = subtitle,
                    icon = if (isRecordedType) Icons.Rounded.FiberManualRecord else Icons.Rounded.Place,
                    isEnabled = isActionSelected,
                    onClick = {
                        if (isActionSelected) {
                            val isRecording = isRecordedType
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                when (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)) {
                                    PackageManager.PERMISSION_GRANTED -> launchAppForAction(isRecording)
                                    else -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else launchAppForAction(isRecording)
                        } else {
                            Toast.makeText(context, "Please select an action first", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // Options & Repeat
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (blockRemoteInput) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                            contentDescription = null,
                            tint = if (blockRemoteInput) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Exclusive Mode",
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

                    if (canRepeat) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
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
        if (label != null) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1, fontSize = 11.sp)
        }
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
        ActionType.DOUBLE_TAP -> Icons.Rounded.TouchApp
        ActionType.TAP -> Icons.Rounded.TouchApp
        ActionType.RECORDED -> Icons.Rounded.Gesture
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
                // Updated Banner ID
                adUnitId = "ca-app-pub-9083635854272688/7237298124"
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}