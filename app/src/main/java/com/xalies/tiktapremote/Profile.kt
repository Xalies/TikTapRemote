package com.xalies.tiktapremote

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey val packageName: String,
    val keyCode: Int,
    val tapX: Int,
    val tapY: Int,
    val blockInput: Boolean,
    val showVisualIndicator: Boolean, // Kept in data, hidden in UI
    val actions: Map<TriggerType, Action> = emptyMap(),
    val repeatInterval: Long = 12000L, // Default 12 seconds
    val isEnabled: Boolean = true
)

// Moved here for global visibility
data class ProfileNavInfo(
    val packageName: String,
    val appName: String,
    val keyCode: Int,
    val tapX: Int,
    val tapY: Int,
    val blockInput: Boolean,
    val showVisualIndicator: Boolean,
    val actions: Map<TriggerType, Action>,
    val isEnabled: Boolean
)