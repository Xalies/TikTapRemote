package com.xalies.tiktapremote

/**
 * A singleton object to temporarily hold recorded gesture data
 * during the recording and profile configuration process.
 */
object GestureRecordingManager {
    var recordedGesture: List<SerializablePath>? = null
}