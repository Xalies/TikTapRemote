package com.xalies.tiktapremote

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
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

    // State for the trial dialog
    var showTrialDialog by remember { mutableStateOf(false) }
    var selectedAppForTrial by remember { mutableStateOf<AppInfo?>(null) }

    val apps = remember {
        val globalProfileAppInfo = AppInfo(
            name = "Global Profile",
            packageName = GLOBAL_PROFILE_PACKAGE_NAME,
            icon = null // We'll handle the icon separately
        )

        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 } // Filter out system apps
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

    if (showTrialDialog && selectedAppForTrial != null) {
        TrialOfferDialog(
            onDismiss = {
                showTrialDialog = false
                selectedAppForTrial = null
            },
            onAccept = {
                repository.activateTrial()
                Toast.makeText(context, "10-Minute Trial Started!", Toast.LENGTH_LONG).show()
                showTrialDialog = false
                // Proceed to the app that was clicked
                onAppClick(selectedAppForTrial!!)
                selectedAppForTrial = null
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
                        // 0. Check if a profile already exists (Duplicate Check)
                        val profileExists = profiles.any { it.packageName == app.packageName }
                        if (profileExists) {
                            Toast.makeText(context, "A profile already exists for this app", Toast.LENGTH_SHORT).show()
                            return@AppListItem
                        }

                        // 1. Always allow Global Profile
                        if (app.packageName == GLOBAL_PROFILE_PACKAGE_NAME) {
                            onAppClick(app)
                        } else {
                            // 2. Free Tier Check
                            val tier = repository.getCurrentTier()
                            if (tier == AppTier.FREE) {
                                if (!repository.hasUsedTrial()) {
                                    // Feature: Trigger Trial Offer
                                    selectedAppForTrial = app
                                    showTrialDialog = true
                                } else {
                                    Toast.makeText(context, "App Profiles require Essentials Tier. Free Tier is Global Only.", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                // Essentials/Pro users allowed (Limit checked at FAB)
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
                contentDescription = null, // Decorative
                modifier = Modifier.size(40.dp)
            )
        }
        Text(
            text = app.name,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AppListScreenPreview() {
    AppListScreen(onAppClick = {}, onBackClick = {})
}