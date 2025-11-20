package com.xalies.tiktapremote

import androidx.room.Entity
import androidx.room.PrimaryKey

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