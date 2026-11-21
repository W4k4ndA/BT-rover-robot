package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ChassisDark
import com.example.ui.theme.ChromeHighlight
import com.example.ui.theme.GunmetalElevated
import com.example.ui.theme.GunmetalSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SteelShadow
import com.example.ui.theme.TitaniumBezel

/**
 * Full-screen robotics chassis background with subtle radial core glow and blueprint hex/grid traces.
 */
@Composable
fun RoboticsBackdrop(
    accentColor: Color = NeonCyan,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .drawBehind {
                // Atmospheric radial cockpit illumination
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.08f),
                            ChassisDark.copy(alpha = 0.7f),
                            ObsidianBg,
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.58f),
                        radius = size.maxDimension * 0.68f,
                    ),
                )
                // Fine metallic micro-grid
                val step = 36.dp.toPx()
                val gridColor = TitaniumBezel.copy(alpha = 0.16f)
                var x = step
                while (x < size.width) {
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f,
                    )
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                    y += step
                }
                // Diagonal industrial corner accents
                val cornerLen = 28.dp.toPx()
                val accentStroke = accentColor.copy(alpha = 0.25f)
                drawLine(accentStroke, Offset(12f, 12f), Offset(12f + cornerLen, 12f), 2f)
                drawLine(accentStroke, Offset(12f, 12f), Offset(12f, 12f + cornerLen), 2f)
            },
        content = content,
    )
}

/**
 * Brushed gunmetal-and-titanium chassis panel with metallic bevel highlight and neon edge accent.
 */
@Composable
fun MetallicPanel(
    modifier: Modifier = Modifier,
    glowColor: Color = TitaniumBezel,
    shape: Shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp, topEnd = 4.dp, bottomStart = 4.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val metalBrush = Brush.linearGradient(
        colors = listOf(
            GunmetalElevated,
            GunmetalSurface,
            ChassisDark,
        ),
        start = Offset(0f, 0f),
        end = Offset(600f, 600f),
    )
    val bevelBrush = Brush.linearGradient(
        colors = listOf(
            glowColor.copy(alpha = 0.75f),
            ChromeHighlight.copy(alpha = 0.45f),
            SteelShadow,
        ),
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(metalBrush)
            .border(BorderStroke(1.2.dp, bevelBrush), shape),
        content = content,
    )
}

/**
 * Brushed steel clickable capsule button with neon rim and 48dp minimum touch target.
 */
@Composable
fun MetallicActionButton(
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val fillBrush = Brush.verticalGradient(
        colors = if (enabled) {
            listOf(
                GunmetalElevated,
                GunmetalSurface,
                ChassisDark,
            )
        } else {
            listOf(ChassisDark, ObsidianBg)
        },
    )
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            accentColor.copy(alpha = if (enabled) 0.9f else 0.3f),
            ChromeHighlight.copy(alpha = if (enabled) 0.6f else 0.2f),
            accentColor.copy(alpha = if (enabled) 0.45f else 0.15f),
        ),
    )
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(fillBrush)
            .border(BorderStroke(1.4.dp, borderBrush), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * Glowing LED status diode indicator.
 */
@Composable
fun StatusLed(
    color: Color,
    size: Dp = 10.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                drawCircle(
                    color = color.copy(alpha = 0.35f),
                    radius = this.size.minDimension * 0.9f,
                )
            }
            .clip(CircleShape)
            .background(color),
    )
}
