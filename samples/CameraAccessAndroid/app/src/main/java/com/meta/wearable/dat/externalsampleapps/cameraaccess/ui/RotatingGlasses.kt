package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R
import kotlin.math.abs
import kotlin.math.cos

// Fakes a 360-degree product-showcase spin using only the 3 still angles we have (front,
// three-quarter, profile) -- there is no real 3D model of the glasses. The classic trick: place
// each photo at the rotation angle where a real turntable would show that view, squish it
// horizontally by |cos(angle)| so it visually narrows to nothing right at the profile angles
// (90/270 degrees, where a real object edge-on would look thinnest too), and cross-fade into the
// next photo right at that thin point where the swap is least noticeable.
private data class RotationStop(val degrees: Float, val drawableRes: Int, val flip: Boolean)

private const val DEGREES_PER_MS = 360f / 8000f
private const val DRAG_RESUME_DELAY_MS = 1200L
private const val DRAG_SENSITIVITY = 0.6f

@Composable
fun RotatingGlasses(modifier: Modifier = Modifier) {
    var angle by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var lastFrameTimeMs by remember { mutableLongStateOf(0L) }
    var resumeAtMs by remember { mutableLongStateOf(0L) }

    // Auto-rotates continuously unless the user is dragging (or just finished dragging --
    // resumeAtMs holds it off briefly so letting go doesn't feel like a snap back to autoplay).
    LaunchedEffect(Unit) {
        var previous = withFrameNanos { it }
        lastFrameTimeMs = previous / 1_000_000L
        while (true) {
            withFrameNanos { frameTime ->
                val dtMs = (frameTime - previous) / 1_000_000f
                previous = frameTime
                lastFrameTimeMs = frameTime / 1_000_000L
                if (!isDragging && lastFrameTimeMs >= resumeAtMs) {
                    angle += dtMs * DEGREES_PER_MS
                }
            }
        }
    }

    val stops = remember {
        listOf(
            RotationStop(0f, R.drawable.glasses_front, flip = false),
            RotationStop(45f, R.drawable.glasses_threequarter, flip = false),
            RotationStop(90f, R.drawable.glasses_profile, flip = false),
            RotationStop(135f, R.drawable.glasses_threequarter, flip = true),
            RotationStop(180f, R.drawable.glasses_front, flip = false),
            RotationStop(225f, R.drawable.glasses_threequarter, flip = false),
            RotationStop(270f, R.drawable.glasses_profile, flip = false),
            RotationStop(315f, R.drawable.glasses_threequarter, flip = true),
        )
    }

    val normalizedAngle = ((angle % 360f) + 360f) % 360f
    val currentIndex = stops.indexOfLast { it.degrees <= normalizedAngle }.coerceAtLeast(0)
    val current = stops[currentIndex]
    val next = stops[(currentIndex + 1) % stops.size]
    val segmentLength = 45f
    val progress = ((normalizedAngle - current.degrees) / segmentLength).coerceIn(0f, 1f)

    // Squish continuously off the live angle (not the per-image segment) so it stays smooth
    // across image swaps instead of jumping.
    val squish = abs(cos(Math.toRadians(normalizedAngle.toDouble()))).toFloat().coerceAtLeast(0.05f)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        resumeAtMs = lastFrameTimeMs + DRAG_RESUME_DELAY_MS
                    },
                    onDragCancel = {
                        isDragging = false
                        resumeAtMs = lastFrameTimeMs + DRAG_RESUME_DELAY_MS
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        angle += dragAmount * DRAG_SENSITIVITY
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(current.drawableRes),
            contentDescription = null,
            modifier = Modifier.graphicsLayer {
                scaleX = squish * (if (current.flip) -1f else 1f)
                alpha = 1f - progress
            },
        )
        Image(
            painter = painterResource(next.drawableRes),
            contentDescription = null,
            modifier = Modifier.graphicsLayer {
                scaleX = squish * (if (next.flip) -1f else 1f)
                alpha = progress
            },
        )
    }
}
