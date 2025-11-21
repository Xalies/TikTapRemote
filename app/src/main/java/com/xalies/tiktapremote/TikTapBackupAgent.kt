package com.xalies.tiktapremote

import android.app.backup.BackupAgentHelper
import android.app.backup.SharedPreferencesBackupHelper

// A simple BackupAgent. Usually not strictly needed if you just use XML rules,
// but since it's referenced in your Manifest, we should provide it to avoid ClassNotFoundException.
class TikTapBackupAgent : BackupAgentHelper() {
    override fun onCreate() {
        // If you needed custom logic, it would go here.
        // For standard XML-based backup, this can be largely empty or just setup helpers.
        // We add a helper for SharedPrefs just in case, though XML rules often supersede this.
        addHelper("prefs", SharedPreferencesBackupHelper(this, "TikTapRemoteSettings"))
    }
}