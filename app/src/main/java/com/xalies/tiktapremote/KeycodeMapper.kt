package com.xalies.tiktapremote

import android.view.KeyEvent

fun mapKeyCodeToString(keyCode: Int): String {
    return when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> "Volume Up"
        KeyEvent.KEYCODE_VOLUME_DOWN -> "Volume Down"
        KeyEvent.KEYCODE_CAMERA -> "Camera"
        KeyEvent.KEYCODE_HEADSETHOOK -> "Headset Hook"
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "Media Play/Pause"
        KeyEvent.KEYCODE_MEDIA_NEXT -> "Media Next"
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "Media Previous"
        KeyEvent.KEYCODE_ENTER -> "Enter"
        KeyEvent.KEYCODE_SPACE -> "Space"
        else -> "Keycode: $keyCode" // Fallback for unknown keys
    }
}