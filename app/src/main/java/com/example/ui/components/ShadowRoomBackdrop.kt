package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.data.model.ShadowPresenceState
import com.example.ui.theme.DeepAmethyst
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PortalCyan
import com.example.ui.theme.RoomDepth
import com.example.ui.theme.RoyalViolet
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private data class AmbientParticle(
    val xFraction: Float,
    val yOffset: Float,
    val speed: Float,
    val radiusDp: Float,
    val alpha: Float,
    val isBlue: Boolean
)

@Composable
fun ShadowRoomBackdrop(
    presenceState: ShadowPresenceState,
    micLevel: Float,
    lowPerformanceMode: Boolean,
    faceImagePath: String?,
    modifier: Modifier = Modifier
) {
    val particles = remember {
        List(28) { idx ->
            val seed = (idx * 73 + 19) % 100
            AmbientParticle(
                xFraction = ((idx * 37 + 11) % 96 + 2) / 100f,
                yOffset = (seed % 100) / 100f,
                speed = 0.25f + ((idx * 17) % 10) * 0.05f,
                radiusDp = 1.1f + ((idx * 13) % 5) * 0.45f,
                alpha = 0.16f + ((idx * 23) % 6) * 0.06f,
                isBlue = idx % 2 == 0
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "shadow_room_motion")

    val breathPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "breath_phase"
    )

    val speechWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "speech_wave"
    )

    val orbitPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_phase"
    )

    val particleDrift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_drift"
    )

    val smoothMic by animateFloatAsState(
        targetValue = if (presenceState == ShadowPresenceState.LISTENING) micLevel else 0f,
        animationSpec = spring(stiffness = 280f),
        label = "mic_smooth"
    )

    val stateIntensity by animateFloatAsState(
        targetValue = when (presenceState) {
            ShadowPresenceState.IDLE -> 0.45f
            ShadowPresenceState.LISTENING -> 0.85f + smoothMic * 0.35f
            ShadowPresenceState.PROCESSING -> 0.95f
            ShadowPresenceState.SPEAKING -> 1.0f
        },
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "state_intensity"
    )

    val effectiveBreath = if (lowPerformanceMode) 0f else breathPhase
    val effectiveSpeech = if (lowPerformanceMode) 0f else speechWave
    val effectiveOrbit = if (lowPerformanceMode) 0f else orbitPhase
    val effectiveDrift = if (lowPerformanceMode) 0f else particleDrift

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val portalCenterX = widthPx * 0.5f
        val portalCenterY = heightPx * 0.41f
        val portalRadius = minOf(widthPx, heightPx) * 0.34f

        val swayOffsetX = when {
            lowPerformanceMode -> 0f
            presenceState == ShadowPresenceState.SPEAKING -> sin(effectiveSpeech * 0.5f) * 3.5f
            else -> sin(effectiveBreath) * 1.5f
        }
        val swayOffsetY = when {
            lowPerformanceMode -> 0f
            presenceState == ShadowPresenceState.SPEAKING -> cos(effectiveSpeech) * 3f
            else -> sin(effectiveBreath * 1.0f) * 4f
        }
        val headTiltDeg = when {
            lowPerformanceMode -> 0f
            presenceState == ShadowPresenceState.SPEAKING -> sin(effectiveSpeech * 0.5f) * 1.8f
            presenceState == ShadowPresenceState.LISTENING -> -1.4f
            else -> sin(effectiveBreath) * 0.7f
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            // 1. Deep cinematic room ambient depth & floor reflection
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(ObsidianVoid, RoomDepth, ObsidianVoid),
                    startY = 0f,
                    endY = size.height
                )
            )

            // Ambient portal glow
            val glowScale = 1f + sin(effectiveBreath) * 0.03f + smoothMic * 0.12f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        RoyalViolet.copy(alpha = 0.28f * stateIntensity),
                        ElectricBlue.copy(alpha = 0.14f * stateIntensity),
                        DeepAmethyst.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(portalCenterX, portalCenterY),
                    radius = portalRadius * 1.95f * glowScale
                ),
                center = Offset(portalCenterX, portalCenterY),
                radius = portalRadius * 1.95f * glowScale
            )

            // Floor perspective ellipse glow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ElectricBlue.copy(alpha = 0.12f * stateIntensity),
                        RoyalViolet.copy(alpha = 0.07f),
                        Color.Transparent
                    ),
                    center = Offset(portalCenterX, heightPx * 0.78f),
                    radius = portalRadius * 1.5f
                ),
                topLeft = Offset(portalCenterX - portalRadius * 1.3f, heightPx * 0.72f),
                size = Size(portalRadius * 2.6f, portalRadius * 0.55f)
            )

            // 2. Floating ambient dust / cognitive particles
            val visibleParticles = if (lowPerformanceMode) particles.take(8) else particles
            visibleParticles.forEach { p ->
                val yNorm = (p.yOffset - effectiveDrift * p.speed + 1f) % 1f
                val px = p.xFraction * size.width
                val py = yNorm * size.height
                val baseColor = if (p.isBlue) ElectricBlue else RoyalViolet
                drawCircle(
                    color = baseColor.copy(alpha = p.alpha * stateIntensity),
                    radius = p.radiusDp.dp.toPx(),
                    center = Offset(px, py)
                )
            }

            // 3. The Mysterious Glowing Portal
            drawPortalRings(
                center = Offset(portalCenterX, portalCenterY),
                radius = portalRadius,
                presenceState = presenceState,
                breath = effectiveBreath,
                orbit = effectiveOrbit,
                micLevel = smoothMic,
                intensity = stateIntensity
            )

            // 4. The One Human-Shaped Black Silhouette Standing Before the Portal
            drawShadowSilhouette(
                center = Offset(portalCenterX + swayOffsetX, portalCenterY + swayOffsetY),
                portalRadius = portalRadius,
                tiltDegrees = headTiltDeg,
                presenceState = presenceState,
                speechWave = effectiveSpeech,
                micLevel = smoothMic,
                intensity = stateIntensity
            )
        }

        // 5. Optional User-Consented Face Compositing inside the Silhouette Head
        if (faceImagePath != null && File(faceImagePath).exists()) {
            val headCenterY = portalCenterY + swayOffsetY - portalRadius * 0.30f
            val headSizePx = (portalRadius * 0.44f).roundToInt().coerceAtLeast(36)
            val headSizeDp = with(density) { headSizePx.toDp() }
            val offsetX = (portalCenterX + swayOffsetX - headSizePx / 2f).roundToInt()
            val offsetY = (headCenterY - headSizePx / 2f).roundToInt()

            val desaturateMatrix = remember {
                ColorMatrix().apply {
                    setToSaturation(0.35f)
                }
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX, offsetY) }
                    .size(headSizeDp)
                    .clip(CircleShape)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = File(faceImagePath)),
                    contentDescription = "Personalized Shadow reflection",
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(desaturateMatrix),
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.56f)
                )
                // Edge shadow vignette so the face melts naturally into the obsidian silhouette
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    RoyalViolet.copy(alpha = 0.14f),
                                    Color(0xBB05050A),
                                    Color(0xFF030307)
                                )
                            )
                        )
                )
            }
        }
    }
}

private fun DrawScope.drawPortalRings(
    center: Offset,
    radius: Float,
    presenceState: ShadowPresenceState,
    breath: Float,
    orbit: Float,
    micLevel: Float,
    intensity: Float
) {
    val pulse = 1f + sin(breath) * 0.02f + micLevel * 0.08f
    val r = radius * pulse

    // Deep dark portal core
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF0A0818),
                Color(0xFF070512),
                Color(0xFF04030B)
            ),
            center = center,
            radius = r
        ),
        center = center,
        radius = r
    )

    // Soft inner portal halo
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                RoyalViolet.copy(alpha = 0.16f * intensity),
                ElectricBlue.copy(alpha = 0.34f * intensity),
                Color.Transparent
            ),
            center = center,
            radius = r * 1.08f
        ),
        center = center,
        radius = r * 1.08f
    )

    // Primary luminous ring
    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(
                ElectricBlue.copy(alpha = 0.85f * intensity),
                RoyalViolet.copy(alpha = 0.90f * intensity),
                PortalCyan.copy(alpha = 0.70f * intensity),
                DeepAmethyst.copy(alpha = 0.65f * intensity),
                ElectricBlue.copy(alpha = 0.85f * intensity)
            ),
            center = center
        ),
        center = center,
        radius = r,
        style = Stroke(width = 3.dp.toPx())
    )

    // Secondary outer whisper ring
    drawCircle(
        color = RoyalViolet.copy(alpha = 0.25f * intensity),
        center = center,
        radius = r * 1.11f,
        style = Stroke(width = 1.dp.toPx())
    )

    // Processing / Listening harmonic arc
    if (presenceState == ShadowPresenceState.PROCESSING || presenceState == ShadowPresenceState.LISTENING) {
        val sweep = if (presenceState == ShadowPresenceState.PROCESSING) 110f else 70f + micLevel * 140f
        drawArc(
            color = PortalCyan.copy(alpha = 0.85f),
            startAngle = orbit,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(center.x - r * 1.06f, center.y - r * 1.06f),
            size = Size(r * 2.12f, r * 2.12f),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = RoyalViolet.copy(alpha = 0.75f),
            startAngle = orbit + 180f,
            sweepAngle = sweep * 0.7f,
            useCenter = false,
            topLeft = Offset(center.x - r * 1.06f, center.y - r * 1.06f),
            size = Size(r * 2.12f, r * 2.12f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawShadowSilhouette(
    center: Offset,
    portalRadius: Float,
    tiltDegrees: Float,
    presenceState: ShadowPresenceState,
    speechWave: Float,
    micLevel: Float,
    intensity: Float
) {
    val headRadiusX = portalRadius * 0.22f
    val headRadiusY = portalRadius * 0.265f
    val headCenterY = center.y - portalRadius * 0.30f

    // Shoulders & torso silhouette rising gracefully from below the portal
    val torsoPath = Path().apply {
        val shoulderY = headCenterY + headRadiusY * 1.38f
        val neckHalfW = headRadiusX * 0.44f
        val shoulderHalfW = portalRadius * 0.68f
        val bottomY = center.y + portalRadius * 1.12f

        moveTo(center.x - neckHalfW, headCenterY + headRadiusY * 0.72f)
        // Left neck curve to shoulder
        cubicTo(
            center.x - neckHalfW * 1.05f, shoulderY - headRadiusY * 0.15f,
            center.x - shoulderHalfW * 0.58f, shoulderY - headRadiusY * 0.08f,
            center.x - shoulderHalfW, shoulderY + portalRadius * 0.32f
        )
        // Left arm / coat line descending smoothly
        lineTo(center.x - shoulderHalfW * 1.04f, bottomY)
        lineTo(center.x + shoulderHalfW * 1.04f, bottomY)
        // Right side up to shoulder
        lineTo(center.x + shoulderHalfW, shoulderY + portalRadius * 0.32f)
        cubicTo(
            center.x + shoulderHalfW * 0.58f, shoulderY - headRadiusY * 0.08f,
            center.x + neckHalfW * 1.05f, shoulderY - headRadiusY * 0.15f,
            center.x + neckHalfW, headCenterY + headRadiusY * 0.72f
        )
        close()
    }

    // Subtle rim light behind shoulders
    drawPath(
        path = torsoPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                ElectricBlue.copy(alpha = 0.55f * intensity),
                RoyalViolet.copy(alpha = 0.30f * intensity),
                Color.Transparent
            ),
            startY = headCenterY,
            endY = center.y + portalRadius
        ),
        style = Stroke(width = 2.5.dp.toPx())
    )

    // Solid obsidian torso fill
    drawPath(
        path = torsoPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF040409),
                Color(0xFF030307),
                ObsidianVoid
            ),
            startY = headCenterY,
            endY = center.y + portalRadius * 1.12f
        )
    )

    // Head silhouette with subtle tilt
    withTransform({
        rotate(degrees = tiltDegrees, pivot = Offset(center.x, headCenterY + headRadiusY * 0.8f))
    }) {
        // Luminous rim light around head
        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(
                    PortalCyan.copy(alpha = 0.65f * intensity),
                    RoyalViolet.copy(alpha = 0.45f * intensity)
                ),
                startY = headCenterY - headRadiusY,
                endY = headCenterY + headRadiusY
            ),
            topLeft = Offset(center.x - headRadiusX - 1.dp.toPx(), headCenterY - headRadiusY - 1.dp.toPx()),
            size = Size((headRadiusX + 1.dp.toPx()) * 2f, (headRadiusY + 1.dp.toPx()) * 2f),
            style = Stroke(width = 2.dp.toPx())
        )

        // Solid obsidian head fill
        drawOval(
            color = Color(0xFF030307),
            topLeft = Offset(center.x - headRadiusX, headCenterY - headRadiusY),
            size = Size(headRadiusX * 2f, headRadiusY * 2f)
        )

        // Subtle synchronized vocal resonance / mouth glow when speaking
        if (presenceState == ShadowPresenceState.SPEAKING) {
            val mouthAmp = (0.35f + 0.65f * ((sin(speechWave * 2.5f) + 1f) * 0.5f))
            val mouthWidth = headRadiusX * 0.48f * (0.8f + 0.25f * mouthAmp)
            val mouthHeight = headRadiusY * 0.08f * mouthAmp + 1.5.dp.toPx()
            val mouthY = headCenterY + headRadiusY * 0.44f

            drawOval(
                color = PortalCyan.copy(alpha = 0.55f * mouthAmp),
                topLeft = Offset(center.x - mouthWidth, mouthY - mouthHeight),
                size = Size(mouthWidth * 2f, mouthHeight * 2f),
                blendMode = BlendMode.Screen
            )
        } else if (presenceState == ShadowPresenceState.LISTENING) {
            val auraAlpha = 0.25f + micLevel * 0.45f
            drawCircle(
                color = ElectricBlue.copy(alpha = auraAlpha),
                radius = headRadiusX * (0.28f + micLevel * 0.15f),
                center = Offset(center.x, headCenterY + headRadiusY * 0.44f)
            )
        }
    }
}
