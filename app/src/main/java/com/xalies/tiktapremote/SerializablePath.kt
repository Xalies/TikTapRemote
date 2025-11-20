package com.xalies.tiktapremote

import java.io.Serializable

/**
 * A serializable representation of a single touch gesture path (stroke).
 *
 * @param points The list of X,Y coordinates for this stroke.
 * @param duration The time it took to draw this stroke (in ms).
 * @param delay The time to wait before starting this stroke (relative to the start of the gesture).
 */
data class SerializablePath(
    val points: List<Point>,
    val duration: Long = 100L,
    val delay: Long = 0L
) : Serializable

/**
 * A simple serializable class to represent a point with x and y coordinates.
 */
data class Point(
    val x: Float,
    val y: Float
) : Serializable