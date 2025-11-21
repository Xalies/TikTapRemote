package com.xalies.tiktapremote

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    modifier: Modifier = Modifier,
    onAppClick: (AppInfo) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val repository = remember { ProfileRepository(context) }

    // Observe profiles to check for duplicates
    val profiles by ProfileManager.profiles.collectAsState()

    // State for the trial selection dialog
    var showTrialSelectionDialog by remember { mutableStateOf(false) }
    var selectedAppForTrial by remember { mutableStateOf<AppInfo?>(null) }

    val apps = remember {
        val globalProfileAppInfo = AppInfo(
            name = "Global Profile",
            packageName = GLOBAL_PROFILE_PACKAGE_NAME,
            icon = null
        )

        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map { appInfo ->
                AppInfo(
                    name = packageManager.getApplicationLabel(appInfo).toString(),
                    packageName = appInfo.packageName,
                    icon = packageManager.getApplicationIcon(appInfo)
                )
            }
            .sortedBy { it.name }
        listOf(globalProfileAppInfo) + installedApps
    }

    if (showTrialSelectionDialog && selectedAppForTrial != null) {
        TrialSelectionDialog(
            isOneTimeTrialAvailable = !repository.hasUsedTrial(),
            onDismiss = {
                showTrialSelectionDialog = false
                selectedAppForTrial = null
            },
            onStartOneTimeTrial = {
                repository.activateTrial()
                Toast.makeText(context, "10-Minute Trial Started!", Toast.LENGTH_LONG).show()
                showTrialSelectionDialog = false
                // Proceed to the app that was clicked
                onAppClick(selectedAppForTrial!!)
                selectedAppForTrial = null
            },
            onWatchAdForTrial = {
                val activity = context as? Activity
                if (activity != null) {
                    AdManager.showRewardedAd(
                        activity = activity,
                        onRewardEarned = {
                            repository.activateAdReward()
                            Toast.makeText(context, "8-Hour Pro Trial Unlocked!", Toast.LENGTH_LONG).show()
                            showTrialSelectionDialog = false
                            onAppClick(selectedAppForTrial!!)
                            selectedAppForTrial = null
                        },
                        onDismissed = {
                            // Ad closed, do nothing unless reward was earned (handled above)
                        }
                    )
                } else {
                    Toast.makeText(context, "Error loading ad context", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Choose Application") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            items(apps) { app ->
                AppListItem(
                    app = app,
                    onClick = {
                        val profileExists = profiles.any { it.packageName == app.packageName }
                        if (profileExists) {
                            Toast.makeText(context, "A profile already exists for this app", Toast.LENGTH_SHORT).show()
                            return@AppListItem
                        }

                        if (app.packageName == GLOBAL_PROFILE_PACKAGE_NAME) {
                            onAppClick(app)
                        } else {
                            val tier = repository.getCurrentTier()
                            // Check if ANY trial is active OR if they have purchased a tier
                            if (tier == AppTier.FREE) {
                                // If neither purchased nor in an active trial, show the selection dialog
                                // (Note: getCurrentTier() returns PRO if a trial is active, so this block only runs if NO trial is active)
                                selectedAppForTrial = app
                                showTrialSelectionDialog = true
                            } else {
                                // Essentials/Pro users (or Active Trial users) allowed
                                onAppClick(app)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AppListItem(
    app: AppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.packageName == GLOBAL_PROFILE_PACKAGE_NAME) {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = "Global Profile",
                modifier = Modifier.size(40.dp)
            )
        } else {
            Image(
                painter = rememberAsyncImagePainter(model = app.icon),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        }
        Text(
            text = app.name,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
fun TrialSelectionDialog(
    isOneTimeTrialAvailable: Boolean,
    onDismiss: () -> Unit,
    onStartOneTimeTrial: () -> Unit,
    onWatchAdForTrial: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Unlock Pro Features?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "To create App Profiles, you need a Pro tier. Choose an option below:",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Option 1: 10 Minute Trial (One-Time)
                if (isOneTimeTrialAvailable) {
                    Button(
                        onClick = onStartOneTimeTrial,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Timer, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("10-Minute Test Drive")
                    }
                    Text(
                        "Instant access. One time only.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Option 2: Watch Ad for 8 Hours (Repeatable)
                Button(
                    onClick = onWatchAdForTrial,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Watch Ad for 8 Hours")
                }
                Text(
                    "Repeatable anytime.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppListScreenPreview() {
    AppListScreen(onAppClick = {}, onBackClick = {})
}