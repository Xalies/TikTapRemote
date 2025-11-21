package com.xalies.tiktapremote

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class RecordingState {
    IDLE,       // Waiting for user to press Start
    RECORDING,  // User is drawing (or waiting to draw)
    CONFIRMING  // Gesture done, asking to save
}

@Composable
fun OverlayView(
    mode: String,
    onConfirmTarget: (x: Int, y: Int) -> Unit = { _, _ -> },
    onGestureRecorded: (List<SerializablePath>) -> Unit = {},
    onCancel: () -> Unit = {}
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (mode == "targeting") {
            TargetingLayout(
                constraints = this,
                onConfirm = onConfirmTarget,
                onCancel = onCancel
            )
        } else {
            RecordingLayout(
                onGestureRecorded = onGestureRecorded,
                onCancel = onCancel
            )
        }
    }
}

@Composable
fun TargetingLayout(
    constraints: BoxWithConstraintsScope,
    onConfirm: (x: Int, y: Int) -> Unit,
    onCancel: () -> Unit
) {
    val iconSize = 48.dp
    val density = LocalDensity.current
    val iconSizePx = with(density) { iconSize.toPx() }

    val screenWidthPx = with(density) { constraints.maxWidth.toPx() }
    val screenHeightPx = with(density) { constraints.maxHeight.toPx() }

    var offsetX by remember { mutableStateOf(screenWidthPx / 2 - iconSizePx / 2) }
    var offsetY by remember { mutableStateOf(screenHeightPx / 2 - iconSizePx / 2) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Crosshair",
            tint = Color.Red,
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(iconSize)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = onCancel) {
                    Text("Cancel")
                }
                Button(onClick = { onConfirm(offsetX.roundToInt() + (iconSizePx/2).roundToInt(), offsetY.roundToInt() + (iconSizePx/2).roundToInt()) }) {
                    Text("Confirm Target")
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RecordingLayout(
    onGestureRecorded: (List<SerializablePath>) -> Unit,
    onCancel: () -> Unit
) {
    var recordingState by remember { mutableStateOf(RecordingState.IDLE) }
    val strokes = remember { mutableStateListOf<SerializablePath>() }
    val currentPoints = remember { mutableStateListOf<Point>() }

    // Timer State
    var recordingTime by remember { mutableStateOf(5) }
    var timerRunning by remember { mutableStateOf(false) }

    // Timestamp tracking for accurate playback
    var gestureSessionStartTime by remember { mutableStateOf(0L) }
    var strokeStartTime by remember { mutableStateOf(0L) }

    // Handle Timer Countdown
    LaunchedEffect(timerRunning) {
        if (timerRunning) {
            while (recordingTime > 0) {
                delay(1000)
                recordingTime--
            }
            if (recordingState == RecordingState.RECORDING) {
                // *** FIX: Save any pending stroke before stopping ***
                if (currentPoints.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    val duration = (now - strokeStartTime).coerceAtLeast(10L)
                    val delay = (strokeStartTime - gestureSessionStartTime).coerceAtLeast(0L)

                    strokes.add(SerializablePath(currentPoints.toList(), duration, delay))
                    currentPoints.clear()
                }
                // ****************************************************

                recordingState = RecordingState.CONFIRMING
                timerRunning = false
            }
        }
    }

    fun startRecording() {
        strokes.clear()
        currentPoints.clear()
        recordingTime = 5
        recordingState = RecordingState.RECORDING
        timerRunning = true

        // Reset timers
        gestureSessionStartTime = 0L
    }

    fun stopRecording() {
        recordingState = RecordingState.CONFIRMING
        timerRunning = false
    }

    fun resetRecording() {
        strokes.clear()
        currentPoints.clear()
        recordingState = RecordingState.IDLE
        timerRunning = false
        recordingTime = 5
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInteropFilter { motionEvent ->
                if (recordingState == RecordingState.RECORDING) {
                    when (motionEvent.action) {
                        MotionEvent.ACTION_DOWN -> {
                            val now = System.currentTimeMillis()
                            if (gestureSessionStartTime == 0L) {
                                gestureSessionStartTime = now
                            }
                            strokeStartTime = now
                            currentPoints.clear()
                            currentPoints.add(Point(motionEvent.x, motionEvent.y))
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val lastPoint = currentPoints.lastOrNull()
                            if (lastPoint == null) {
                                currentPoints.add(Point(motionEvent.x, motionEvent.y))
                            } else {
                                // *** THROTTLING LOGIC ***
                                // Reverted to 20 pixels for smoother curves now that timeout bug is fixed
                                val dx = abs(motionEvent.x - lastPoint.x)
                                val dy = abs(motionEvent.y - lastPoint.y)
                                if (dx >= 20 || dy >= 20) {
                                    currentPoints.add(Point(motionEvent.x, motionEvent.y))
                                }
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (currentPoints.isNotEmpty()) {
                                val now = System.currentTimeMillis()
                                val duration = (now - strokeStartTime).coerceAtLeast(10L)
                                // Calculate delay relative to start
                                val delay = (strokeStartTime - gestureSessionStartTime).coerceAtLeast(0L)

                                strokes.add(SerializablePath(currentPoints.toList(), duration, delay))
                            }
                            currentPoints.clear()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }) {
            // Draw previous strokes
            strokes.forEach { stroke ->
                if (stroke.points.size > 1) {
                    val path = Path()
                    path.moveTo(stroke.points.first().x, stroke.points.first().y)
                    stroke.points.forEach { point -> path.lineTo(point.x, point.y) }
                    drawPath(path, color = Color.Red, style = Stroke(width = 5f))
                } else if (stroke.points.size == 1) {
                    // Draw tap as a small circle/point
                    drawCircle(color = Color.Red, radius = 5f, center = androidx.compose.ui.geometry.Offset(stroke.points.first().x, stroke.points.first().y))
                }
            }
            // Draw current stroke
            if (currentPoints.size > 1) {
                val path = Path()
                path.moveTo(currentPoints.first().x, currentPoints.first().y)
                currentPoints.forEach { point -> path.lineTo(point.x, point.y) }
                drawPath(path, color = Color.Red, style = Stroke(width = 5f))
            } else if (currentPoints.size == 1) {
                drawCircle(color = Color.Red, radius = 5f, center = androidx.compose.ui.geometry.Offset(currentPoints.first().x, currentPoints.first().y))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val bannerText = when (recordingState) {
                RecordingState.IDLE -> "Ready to Record"
                RecordingState.RECORDING -> "Recording... $recordingTime s"
                else -> "Gesture Captured"
            }

            Text(
                text = bannerText,
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (recordingState) {
                    RecordingState.IDLE -> {
                        Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                            Text("Cancel")
                        }
                        Button(onClick = { startRecording() }) {
                            Text("Start Rec")
                        }
                    }
                    RecordingState.RECORDING -> {
                        Button(onClick = { stopRecording() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                            Text("Stop")
                        }
                    }
                    RecordingState.CONFIRMING -> {
                        Button(onClick = { resetRecording() }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                            Text("Retry")
                        }
                        Button(onClick = { onGestureRecorded(strokes.toList()) }) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TargetingLayoutPreview() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        TargetingLayout(constraints = this, onConfirm = { _, _ -> }, onCancel = {})
    }
}

@Preview(showBackground = true)
@Composable
fun RecordingLayoutPreview() {
    RecordingLayout(onGestureRecorded = {}, onCancel = {})
}