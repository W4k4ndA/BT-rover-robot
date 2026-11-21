package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.ChassisDark
import com.example.ui.theme.ChromeHighlight
import com.example.ui.theme.GunmetalElevated
import com.example.ui.theme.GunmetalSurface
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SteelShadow
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TitaniumBezel
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Joystick virtual futurista con carcasa metálica cepillada, haz vectorial neón,
 * marcas radiales y física de retorno por resorte al soltar.
 *
 * Emite coordenadas normalizadas:
 * - joyX en [-1f, +1f] (-1 = izquierda, +1 = derecha)
 * - joyY en [-1f, +1f] (+1 = arriba/adelante, -1 = abajo/reversa)
 */
@Composable
fun FuturisticJoystick(
    onPositionChanged: (joyX: Float, joyY: Float, isReleased: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentCallback by rememberUpdatedState(onPositionChanged)

    // Animatable permite actualizar la posición al instante durante el arrastre
    // y animar el retorno elástico al centro sin perder fluidez de 60/120 Hz.
    val normXAnim = remember { Animatable(0f) }
    val normYAnim = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var wasEdgeHapticTriggered by remember { mutableStateOf(false) }

    val joystickCd = stringResource(R.string.cd_joystick)
    val labelFwd = stringResource(R.string.joy_fwd)
    val labelRev = stringResource(R.string.joy_rev)
    val labelLeft = stringResource(R.string.joy_left)
    val labelRight = stringResource(R.string.joy_right)
    val labelHint = stringResource(R.string.joy_hint_drag)

    BoxWithConstraints(
        modifier = modifier
            .semantics { contentDescription = joystickCd }
            .testTag("virtual_joystick"),
        contentAlignment = Alignment.Center,
    ) {
        val sideDp = min(maxWidth.value, maxHeight.value).dp.coerceIn(220.dp, 350.dp)

        Box(
            modifier = Modifier.size(sideDp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            isDragging = true
                            wasEdgeHapticTriggered = false
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                            val center = Offset(size.width / 2f, size.height / 2f)
                            val maxRadius = (min(size.width, size.height) * 0.34f).coerceAtLeast(1f)

                            fun updateFromPointer(pos: Offset) {
                                val dx = pos.x - center.x
                                val dy = pos.y - center.y
                                val dist = hypot(dx, dy)
                                // Restringimos el puntero al perímetro circular del joystick
                                val clampedDist = min(dist, maxRadius)
                                val ratio = if (dist > 0.001f) clampedDist / dist else 0f

                                val nX = ((dx * ratio) / maxRadius).coerceIn(-1f, 1f)
                                // En pantalla Y crece hacia abajo, por eso invertimos el signo
                                // para que arrastrar hacia ARRIBA sea +1.0 (avanzar adelante).
                                val nY = (-(dy * ratio) / maxRadius).coerceIn(-1f, 1f)

                                // Feedback háptico al alcanzar el límite máximo de potencia
                                if (dist >= maxRadius * 0.96f && !wasEdgeHapticTriggered) {
                                    wasEdgeHapticTriggered = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else if (dist < maxRadius * 0.85f) {
                                    wasEdgeHapticTriggered = false
                                }

                                scope.launch {
                                    normXAnim.snapTo(nX)
                                    normYAnim.snapTo(nY)
                                }
                                currentCallback(nX, nY, false)
                            }

                            updateFromPointer(down.position)

                            while (true) {
                                val event = awaitPointerEvent()
                                val activeChange = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                if (activeChange == null || !activeChange.pressed) {
                                    activeChange?.consume()
                                    break
                                }
                                activeChange.consume()
                                updateFromPointer(activeChange.position)
                            }

                            // Al soltar el dedo enviamos paro inmediato (0,0) al ESP32
                            // mientras la perilla regresa visualmente con efecto resorte.
                            isDragging = false
                            currentCallback(0f, 0f, true)
                            scope.launch {
                                launch {
                                    normXAnim.animateTo(
                                        0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium,
                                        ),
                                    )
                                }
                                launch {
                                    normYAnim.animateTo(
                                        0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium,
                                        ),
                                    )
                                }
                            }
                        }
                    },
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = min(size.width, size.height) * 0.46f
                val travelRadius = min(size.width, size.height) * 0.34f
                val puckRadius = min(size.width, size.height) * 0.135f

                val curX = normXAnim.value
                val curY = normYAnim.value
                val mag = hypot(curX, curY).coerceIn(0f, 1f)

                val activeNeon = when {
                    mag < 0.08f -> NeonCyan
                    curY < -0.25f -> NeonCrimson
                    kotlin.math.abs(curX) > kotlin.math.abs(curY) -> NeonViolet
                    else -> NeonCyan
                }

                // 1. Outer Metallic Bezel Ring
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(ChromeHighlight, TitaniumBezel, SteelShadow),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                    ),
                    radius = outerRadius,
                    center = center,
                )

                // 2. Recessed Obsidian Telemetry Dish
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(GunmetalSurface, ChassisDark, ObsidianBg),
                        center = center,
                        radius = outerRadius * 0.94f,
                    ),
                    radius = outerRadius * 0.93f,
                    center = center,
                )

                // 3. Outer Neon Halo Rim
                drawCircle(
                    color = activeNeon.copy(alpha = if (isDragging) 0.85f else 0.38f),
                    radius = outerRadius * 0.93f,
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx()),
                )

                // 4. Concentric Radar Rings & Crosshairs
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                drawCircle(
                    color = TitaniumBezel.copy(alpha = 0.6f),
                    radius = travelRadius,
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx(), pathEffect = dashEffect),
                )
                drawCircle(
                    color = TitaniumBezel.copy(alpha = 0.45f),
                    radius = travelRadius * 0.55f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )

                // Crosshair axes
                drawLine(
                    color = TitaniumBezel.copy(alpha = 0.45f),
                    start = Offset(center.x - travelRadius, center.y),
                    end = Offset(center.x + travelRadius, center.y),
                    strokeWidth = 1.dp.toPx(),
                )
                drawLine(
                    color = TitaniumBezel.copy(alpha = 0.45f),
                    start = Offset(center.x, center.y - travelRadius),
                    end = Offset(center.x, center.y + travelRadius),
                    strokeWidth = 1.dp.toPx(),
                )

                // 5. Radial Degree Ticks around Bezel
                for (deg in 0 until 360 step 15) {
                    val rad = deg * (PI / 180.0)
                    val isCardinal = deg % 90 == 0
                    val rStart = outerRadius * (if (isCardinal) 0.84f else 0.88f)
                    val rEnd = outerRadius * 0.92f
                    val startPt = Offset(
                        center.x + (cos(rad) * rStart).toFloat(),
                        center.y + (sin(rad) * rStart).toFloat(),
                    )
                    val endPt = Offset(
                        center.x + (cos(rad) * rEnd).toFloat(),
                        center.y + (sin(rad) * rEnd).toFloat(),
                    )
                    drawLine(
                        color = if (isCardinal) activeNeon.copy(alpha = 0.85f) else ChromeHighlight.copy(alpha = 0.5f),
                        start = startPt,
                        end = endPt,
                        strokeWidth = if (isCardinal) 2.5.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                // 6. Thumb Coordinates & Dynamic Thrust Beam
                val thumbCenter = Offset(
                    x = center.x + curX * travelRadius,
                    y = center.y - curY * travelRadius,
                )

                if (mag > 0.05f) {
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(activeNeon.copy(alpha = 0.25f), activeNeon),
                            start = center,
                            end = thumbCenter,
                        ),
                        start = center,
                        end = thumbCenter,
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                // 7. Ambient Glow Behind Thumb Puck
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            activeNeon.copy(alpha = if (isDragging) 0.45f else 0.2f),
                            Color.Transparent,
                        ),
                        center = thumbCenter,
                        radius = puckRadius * 1.9f,
                    ),
                    radius = puckRadius * 1.9f,
                    center = thumbCenter,
                )

                // 8. Brushed Metallic Thumbstick Puck (3D Bevel)
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(ChromeHighlight, GunmetalElevated, SteelShadow),
                        start = thumbCenter - Offset(puckRadius, puckRadius),
                        end = thumbCenter + Offset(puckRadius, puckRadius),
                    ),
                    radius = puckRadius,
                    center = thumbCenter,
                )
                drawCircle(
                    color = activeNeon,
                    radius = puckRadius,
                    center = thumbCenter,
                    style = Stroke(width = 2.dp.toPx()),
                )

                // Inner machined cap
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(GunmetalSurface, ChassisDark),
                        center = thumbCenter,
                        radius = puckRadius * 0.72f,
                    ),
                    radius = puckRadius * 0.72f,
                    center = thumbCenter,
                )

                // Glowing Center Reactor Core
                drawCircle(
                    color = activeNeon,
                    radius = puckRadius * 0.26f,
                    center = thumbCenter,
                )
            }

            // Directional Axis Labels inside/around bezel
            Text(
                text = "▲ $labelFwd",
                style = MaterialTheme.typography.labelSmall,
                color = if (normYAnim.value > 0.15f) NeonCyan else TextSecondary,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp),
            )
            Text(
                text = "▼ $labelRev",
                style = MaterialTheme.typography.labelSmall,
                color = if (normYAnim.value < -0.15f) NeonCrimson else TextSecondary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp),
            )
            Text(
                text = labelLeft,
                style = MaterialTheme.typography.labelSmall,
                color = if (normXAnim.value < -0.15f) NeonViolet else TextSecondary,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 18.dp),
            )
            Text(
                text = labelRight,
                style = MaterialTheme.typography.labelSmall,
                color = if (normXAnim.value > 0.15f) NeonViolet else TextSecondary,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp),
            )

            if (!isDragging && hypot(normXAnim.value, normYAnim.value) < 0.05f) {
                Text(
                    text = labelHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 44.dp),
                )
            }
        }
    }
}
