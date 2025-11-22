package com.xalies.tiktapremote

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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

    val profiles by ProfileManager.profiles.collectAsState()

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
                Toast.makeText(context, context.getString(R.string.toast_trial_10m_started), Toast.LENGTH_LONG).show()
                showTrialSelectionDialog = false
                onAppClick(selectedAppForTrial!!)
                selectedAppForTrial = null
            },
            onWatchAdForTrial = {
                val activity = context as? Activity
                if (activity != null) {
                    AdManager.showRewardedAd(
                        activity = activity,
                        onRewardEarned = {
                            // Updated to use addAdRewardTime for 10 hours
                            repository.addAdRewardTime()
                            Toast.makeText(context, context.getString(R.string.toast_trial_ad_unlocked), Toast.LENGTH_LONG).show()
                            showTrialSelectionDialog = false
                            onAppClick(selectedAppForTrial!!)
                            selectedAppForTrial = null
                        },
                        onDismissed = {}
                    )
                } else {
                    Toast.makeText(context, context.getString(R.string.toast_ad_failed), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_choose_app)) },
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
                            Toast.makeText(context, context.getString(R.string.toast_profile_exists), Toast.LENGTH_SHORT).show()
                            return@AppListItem
                        }

                        if (app.packageName == GLOBAL_PROFILE_PACKAGE_NAME) {
                            onAppClick(app)
                            return@AppListItem
                        }

                        val tier = repository.getCurrentTier()

                        if (tier == AppTier.FREE) {
                            selectedAppForTrial = app
                            showTrialSelectionDialog = true
                        } else {
                            val currentAppCount = profiles.count { it.packageName != GLOBAL_PROFILE_PACKAGE_NAME }
                            val limit = repository.getMaxAppProfiles()

                            if (currentAppCount >= limit) {
                                Toast.makeText(context, context.getString(R.string.toast_profile_limit, limit), Toast.LENGTH_LONG).show()
                            } else {
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
            text = if (app.packageName == GLOBAL_PROFILE_PACKAGE_NAME) stringResource(R.string.global_profile_name) else app.name,
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
                    stringResource(R.string.dialog_unlock_pro),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.dialog_unlock_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isOneTimeTrialAvailable) {
                    Button(
                        onClick = onStartOneTimeTrial,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Timer, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_trial_10m))
                    }
                    Text(
                        stringResource(R.string.desc_trial_10m),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                Button(
                    onClick = onWatchAdForTrial,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_trial_ad)) // 10 Hours text
                }
                Text(
                    stringResource(R.string.desc_trial_ad),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        }
    }
}