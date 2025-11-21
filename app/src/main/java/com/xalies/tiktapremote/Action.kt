package com.xalies.tiktapremote

import java.io.Serializable

/**
 * This data class holds the configuration for a single action.
 *
 * @param type The type of action to perform.
 * @param recordedGesture A list of serializable paths representing a recorded gesture.
 * @param tapX The X coordinate for a TAP or SWIPE action.
 * @param tapY The Y coordinate for a TAP or SWIPE action.
 */
data class Action(
    val type: ActionType,
    val recordedGesture: List<SerializablePath>? = null,
    val tapX: Int = 0,
    val tapY: Int = 0
) : Serializable