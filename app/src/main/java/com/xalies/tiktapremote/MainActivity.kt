package com.xalies.tiktapremote

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
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

    // *** ADDED: Track Tier for UI Logic ***
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
                "Global Profile"
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
                // Refresh tier to ensure locked state is accurate
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
                title = { Text("TikTap Remote") },
                actions = {
                    IconButton(onClick = onUpgradeClick) {
                        Icon(Icons.Default.ShoppingCart, "Upgrade")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (!isAccessibilityServiceEnabled) {
                    Toast.makeText(context, "Please Grant Accessibility", Toast.LENGTH_SHORT).show()
                    return@FloatingActionButton
                }
                if (!hasOverlayPermission) {
                    Toast.makeText(context, "Please enable 'Draw Over Other Apps' first", Toast.LENGTH_SHORT).show()
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                    context.startActivity(intent)
                    return@FloatingActionButton
                }

                val tier = repository.getCurrentTier()
                val currentAppCount = profiles.count { it.packageName != GLOBAL_PROFILE_PACKAGE_NAME }

                if (tier == AppTier.ESSENTIALS && currentAppCount >= 2) {
                    Toast.makeText(context, "Essential Tier limited to 2 profiles, delete one first or upgrade to a pro tier", Toast.LENGTH_LONG).show()
                } else {
                    onAddProfileClick()
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Profile")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            // Master Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Enable TikTap Service",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isServiceGloballyEnabled,
                    onCheckedChange = { isEnabled ->
                        isServiceGloballyEnabled = isEnabled
                        repository.setServiceEnabled(isEnabled)

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
                                currentTier = newTier // Update local state immediately
                                Toast.makeText(context, "🕵️ Debug: Switched to ${newTier.name}", Toast.LENGTH_LONG).show()
                            }
                            debugClickCount = 0
                        }
                    }
                )
            }

            // Haptic Toggle
            Row(
                modifier = Modifier.fillMaxWidth().padding(top=8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Vibration, null, tint = MaterialTheme.colorScheme.secondary)
                Text(
                    "Haptic Feedback",
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
                Text("Required Permissions", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                if (!hasOverlayPermission) {
                    PermissionCheck(
                        permissionName = "Draw Over Other Apps",
                        onClick = {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                            context.startActivity(intent)
                        }
                    )
                }
                if (!isAccessibilityServiceEnabled) {
                    PermissionCheck(
                        permissionName = "Accessibility Service",
                        onClick = {
                            showAccessibilityDisclosure = true
                        }
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                    PermissionCheck(
                        permissionName = "Send Notifications",
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
                        text = "No profiles saved yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tap the '+' button to create one",
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
                            currentTier = currentTier, // Pass tier to item
                            onClick = { onProfileClick(profileInfo) },
                            onLongClick = { profileToDelete = originalProfile },
                            onDoubleClick = {
                                if (profileInfo.packageName != GLOBAL_PROFILE_PACKAGE_NAME) {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(profileInfo.packageName)
                                    if (launchIntent != null) {
                                        context.startActivity(launchIntent)
                                    } else {
                                        Toast.makeText(context, "Cannot launch this app directly", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Cannot launch Global Profile", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onToggleEnabled = {
                                if (originalProfile != null) {
                                    val newStatus = !originalProfile.isEnabled

                                    // Check limits ONLY if turning ON and it is NOT the Global Profile
                                    if (newStatus && originalProfile.packageName != GLOBAL_PROFILE_PACKAGE_NAME) {
                                        val limit = repository.getMaxAppProfiles()
                                        val currentEnabled = profiles.count { it.packageName != GLOBAL_PROFILE_PACKAGE_NAME && it.isEnabled }

                                        if (currentEnabled >= limit) {
                                            Toast.makeText(context, "Limit reached ($limit enabled profiles). Upgrade to enable more.", Toast.LENGTH_LONG).show()
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
        title = { Text("Accessibility Service Usage") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("To translate your remote's button presses into on-screen actions, TikTap Remote requires Accessibility Service permissions. Here’s exactly why we need them:")
                Text("1. Perform Gestures:", fontWeight = FontWeight.Bold)
                Text("This is the app's core function. It allows us to programmatically perform the Taps, Swipes, and Double Taps you configure.")
                Text("2. Retrieve Window Content:", fontWeight = FontWeight.Bold)
                Text("We only use this to identify the package name of the app currently in the foreground (e.g., TikTok). This is essential to know which of your saved profiles to activate.")
                Text("3. Observe Your Actions:", fontWeight = FontWeight.Bold)
                Text("We use this specifically to capture hardware key presses from your Bluetooth remote. We use a modern flag that explicitly filters out any on-screen keyboard events, ensuring we cannot see anything you type.")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Continue to Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
            Text("Grant")
        }
    }
}

@Composable
fun DeleteConfirmationDialog(profileName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Profile") },
        text = { Text("Are you sure you want to delete the profile for \"$profileName\"?") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileListItem(
    profileNavInfo: ProfileNavInfo,
    currentTier: AppTier, // Added
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

    // Determine if this profile is effectively "locked" due to tier limits
    // Logic: It's an App Profile (not Global), it's disabled, and user is on FREE tier.
    // (Normally, free tier allows 0 app profiles, so any disabled app profile is effectively locked).
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
            .alpha(if (isLocked) 0.5f else 1f), // Grey out if locked
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
                    text = "Trial Expired (Locked)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (isLocked) {
            // Show Lock icon instead of checkbox
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

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    TikTapRemoteTheme {
        MainScreen(onAddProfileClick = {}, onUpgradeClick = {}, onProfileClick = {})
    }
}