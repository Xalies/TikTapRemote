package com.xalies.tiktapremote

import java.io.Serializable

/**
 * This data class holds the configuration for a single action.
 *
 * @param type The type of action to perform.
 * @param recordedGesture A list of serializable paths representing a recorded gesture.
 *                        This is only used when the type is [ActionType.RECORDED].
 */
data class Action(
    val type: ActionType,
    val recordedGesture: List<SerializablePath>? = null
) : Serializable