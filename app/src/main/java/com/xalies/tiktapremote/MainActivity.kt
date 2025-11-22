package com.xalies.tiktapremote

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter
import com.xalies.tiktapremote.ui.theme.TikTapRemoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TikTapRemoteTheme {
                AppNavHost()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onAddProfileClick: () -> Unit,
    onUpgradeClick: () -> Unit,
    onProfileClick: (ProfileNavInfo) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { ProfileRepository(context) }

    val profilesFlow = ProfileManager.profiles.collectAsState()
    val profiles = profilesFlow.value

    var currentTier by remember { mutableStateOf(repository.getCurrentTier()) }

    var profileToDelete by remember { mutableStateOf<Profile?>(null) }
    var isServiceGloballyEnabled by remember { mutableStateOf(repository.isServiceEnabled()) }
    var isHapticEnabled by remember { mutableStateOf(repository.isHapticEnabled()) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    var debugClickCount by remember { mutableIntStateOf(0) }
    var lastClickTime by remember { mutableLongStateOf(0L) }

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isAccessibilityServiceEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasNotificationPermission = isGranted }
    )

    val profileNavInfos = remember(profiles) {
        profiles.map { profile ->
            val appName = if (profile.packageName == GLOBAL_PROFILE_PACKAGE_NAME) {
                // FIX: Use context.getString() instead of stringResource() inside remember block
                context.getString(R.string.global_profile_name)
            } else {
                try {
                    val appInfo = context.packageManager.getApplicationInfo(profile.packageName, 0)
                    context.packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    profile.packageName
                }
            }
            ProfileNavInfo(
                packageName = profile.packageName,
                appName = appName,
                keyCode = profile.keyCode,
                tapX = profile.tapX,
                tapY = profile.tapY,
                blockInput = profile.blockInput,
                showVisualIndicator = profile.showVisualIndicator,
                actions = profile.actions,
                isEnabled = profile.isEnabled
            )
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceGloballyEnabled = repository.isServiceEnabled()
                currentTier = repository.getCurrentTier()
                hasOverlayPermission = Settings.canDrawOverlays(context)
                isAccessibilityServiceEnabled = isAccessibilityServiceEnabled(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotificationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                }
                ProfileManager.checkAndEnforceLimits()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onConfirm = {
                showAccessibilityDisclosure = false
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            },
            onDismiss = {
                showAccessibilityDisclosure = false
            }
        )
    }

    if (profileToDelete != null) {
        val appNameToDelete = profileNavInfos.find { it.packageName == profileToDelete?.packageName }?.appName ?: profileToDelete!!.packageName
        DeleteConfirmationDialog(
            profileName = appNameToDelete,
            onConfirm = {
                ProfileManager.deleteProfile(profileToDelete!!.packageName)
                profileToDelete = null
            },
            onDismiss = { profileToDelete = null }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = {
                        val activity = context as? Activity
                        if (activity != null) {
                            AdManager.showRewardedAd(
                                activity = activity,
                                onRewardEarned = {
                                    repository.addAdRewardTime()
                                    currentTier = repository.getCurrentTier()
                                    Toast.makeText(context, context.getString(R.string.toast_trial_extended), Toast.LENGTH_LONG).show()
                                },
                                onDismissed = {}
                            )
                        } else {
                            Toast.makeText(context, context.getString(R.string.toast_ad_failed), Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.AccessTime, "Extend Trial")
                    }

                    IconButton(onClick = onUpgradeClick) {
                        Icon(Icons.Default.ShoppingCart, "Upgrade")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (!isAccessibilityServiceEnabled) {
                    Toast.makeText(context, context.getString(R.string.toast_grant_access), Toast.LENGTH_SHORT).show()
                    return@FloatingActionButton
                }
                if (!hasOverlayPermission) {
                    Toast.makeText(context, context.getString(R.string.toast_grant_overlay), Toast.LENGTH_SHORT).show()
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                    context.startActivity(intent)
                    return@FloatingActionButton
                }

                val tier = repository.getCurrentTier()
                val currentAppCount = profiles.count { it.packageName != GLOBAL_PROFILE_PACKAGE_NAME }

                if (tier == AppTier.ESSENTIALS && currentAppCount >= 2) {
                    Toast.makeText(context, context.getString(R.string.toast_limit_essential), Toast.LENGTH_LONG).show()
                } else {
                    onAddProfileClick()
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Profile")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.master_switch_label),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isServiceGloballyEnabled,
                    onCheckedChange = { isEnabled ->
                        isServiceGloballyEnabled = isEnabled
                        repository.setServiceEnabled(isEnabled)

                        // FIX: BuildConfig is now recognized after build.gradle update
                        if (BuildConfig.DEBUG) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastClickTime < 500) {
                                debugClickCount++
                            } else {
                                debugClickCount = 1
                            }
                            lastClickTime = currentTime

                            if (debugClickCount >= 10) {
                                if (repository.isBackdoorActive()) {
                                    repository.setBackdoorUsed(true)
                                    val newTier = repository.cycleTier()
                                    currentTier = newTier
                                    Toast.makeText(context, context.getString(R.string.toast_debug_switched, newTier.name), Toast.LENGTH_LONG).show()
                                }
                                debugClickCount = 0
                            }
                        }
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top=8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Vibration, null, tint = MaterialTheme.colorScheme.secondary)
                Text(
                    stringResource(R.string.haptic_label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isHapticEnabled,
                    onCheckedChange = { isEnabled ->
                        isHapticEnabled = isEnabled
                        repository.setHapticEnabled(isEnabled)
                    },
                    modifier = Modifier.scale(0.8f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!hasOverlayPermission || !isAccessibilityServiceEnabled || !hasNotificationPermission) {
                Text(stringResource(R.string.required_permissions_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                if (!hasOverlayPermission) {
                    PermissionCheck(
                        permissionName = stringResource(R.string.perm_overlay),
                        onClick = {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                            context.startActivity(intent)
                        }
                    )
                }
                if (!isAccessibilityServiceEnabled) {
                    PermissionCheck(
                        permissionName = stringResource(R.string.perm_accessibility),
                        onClick = {
                            showAccessibilityDisclosure = true
                        }
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                    PermissionCheck(
                        permissionName = stringResource(R.string.perm_notification),
                        onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (profileNavInfos.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.no_profiles_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.no_profiles_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(profileNavInfos) { profileInfo ->
                        val originalProfile = profiles.find { it.packageName == profileInfo.packageName }
                        ProfileListItem(
                            profileNavInfo = profileInfo,
                            currentTier = currentTier,
                            onClick = { onProfileClick(profileInfo) },
                            onLongClick = { profileToDelete = originalProfile },
                            onDoubleClick = {
                                if (profileInfo.packageName != GLOBAL_PROFILE_PACKAGE_NAME) {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(profileInfo.packageName)
                                    if (launchIntent != null) {
                                        context.startActivity(launchIntent)
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.toast_cannot_launch), Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, context.getString(R.string.toast_cannot_launch_global), Toast.LENGTH_SHORT).show()
                                }
                            },
                            onToggleEnabled = {
                                if (originalProfile != null) {
                                    val newStatus = !originalProfile.isEnabled
                                    if (newStatus && originalProfile.packageName != GLOBAL_PROFILE_PACKAGE_NAME) {
                                        val limit = repository.getMaxAppProfiles()
                                        val currentEnabled = profiles.count { it.packageName != GLOBAL_PROFILE_PACKAGE_NAME && it.isEnabled }

                                        if (currentEnabled >= limit) {
                                            Toast.makeText(context, context.getString(R.string.toast_limit_reached, limit), Toast.LENGTH_LONG).show()
                                            return@ProfileListItem
                                        }
                                    }
                                    val updatedProfile = originalProfile.copy(isEnabled = newStatus)
                                    ProfileManager.updateProfile(updatedProfile)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AccessibilityDisclosureDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_access_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.dialog_access_desc))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.btn_continue_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
fun PermissionCheck(permissionName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(permissionName)
        Button(onClick = onClick) {
            Text(stringResource(R.string.btn_grant))
        }
    }
}

@Composable
fun DeleteConfirmationDialog(profileName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_delete_title)) },
        text = { Text(stringResource(R.string.dialog_delete_msg, profileName)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.btn_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileListItem(
    profileNavInfo: ProfileNavInfo,
    currentTier: AppTier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val appIcon = try {
        pm.getApplicationIcon(profileNavInfo.packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    val isLocked = !profileNavInfo.isEnabled &&
            profileNavInfo.packageName != GLOBAL_PROFILE_PACKAGE_NAME &&
            currentTier == AppTier.FREE

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = onDoubleClick
            )
            .padding(vertical = 8.dp)
            .alpha(if (isLocked) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (profileNavInfo.packageName == GLOBAL_PROFILE_PACKAGE_NAME) {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = "Global Profile",
                modifier = Modifier.size(40.dp)
            )
        } else {
            Image(
                painter = rememberAsyncImagePainter(model = appIcon),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        }

        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = profileNavInfo.appName,
                fontWeight = FontWeight.Normal
            )
            if (isLocked) {
                Text(
                    text = stringResource(R.string.profile_trial_expired),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (isLocked) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Checkbox(
                checked = profileNavInfo.isEnabled,
                onCheckedChange = { onToggleEnabled() }
            )
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    return enabledServices.any { it.id.contains(context.packageName) }
}