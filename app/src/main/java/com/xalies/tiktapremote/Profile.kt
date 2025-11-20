package com.xalies.tiktapremote

data class Profile(
    val packageName: String,
    val keyCode: Int,
    val tapX: Int,
    val tapY: Int,
    val blockInput: Boolean,
    val showVisualIndicator: Boolean, // Kept in data, hidden in UI
    val actions: Map<TriggerType, Action> = emptyMap(),
    val repeatInterval: Long = 12000L, // Default 12 seconds
    val isEnabled: Boolean = true
)