package com.genius.downloader.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.genius.downloader.ui.theme.Cyan
import com.genius.downloader.ui.theme.Magenta
import com.genius.downloader.ui.theme.Violet

/** Le gros bouton rond noir lumineux. */
@Composable
fun GlowButton(
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 190.dp,
) {
    val inf = rememberInfiniteTransition(label = "glow")
    val rotation by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(if (loading) 1100 else 7000, easing = LinearEasing)),
        label = "rot",
    )
    val pulse by inf.animateFloat(
        0.55f, 1f,
        infiniteRepeatable(tween(if (loading) 600 else 1800), RepeatMode.Reverse),
        label = "pulse",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.45f), label = "scale")
    val ring = listOf(Cyan, Violet, Magenta, Cyan)

    Box(modifier.size(diameter * 1.45f), contentAlignment = Alignment.Center) {
        // Halo lumineux
        Canvas(Modifier.size(diameter * 1.45f).scale(scale)) {
            val r = size.minDimension / 2
            drawCircle(
                Brush.radialGradient(
                    0.45f to Cyan.copy(alpha = 0.32f * pulse),
                    0.7f to Magenta.copy(alpha = 0.14f * pulse),
                    1f to Color.Transparent,
                    center = center, radius = r,
                ),
                radius = r,
            )
        }
        Box(
            Modifier
                .size(diameter)
                .scale(scale)
                .drawBehind {
                    val stroke = 5.dp.toPx()
                    rotate(rotation) {
                        // lueur douce + anneau net
                        drawCircle(Brush.sweepGradient(ring, center), radius = size.minDimension / 2 - stroke,
                            style = Stroke(stroke * 3.2f), alpha = 0.25f * pulse)
                        drawCircle(Brush.sweepGradient(ring, center), radius = size.minDimension / 2 - stroke,
                            style = Stroke(stroke))
                    }
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF22222E), Color(0xFF0B0B10), Color.Black),
                        center = Offset.Unspecified,
                    ),
                )
                .clickable(interaction, ripple(color = Cyan), enabled = !loading, onClick = onClick)
                .semantics { contentDescription = "Analyser le lien" },
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(diameter * 0.38f), color = Cyan, strokeWidth = 4.dp,
                    trackColor = Color.White.copy(alpha = 0.06f))
            } else {
                Icon(
                    Icons.Rounded.Download, null,
                    Modifier.size(diameter * 0.4f),
                    tint = Color.White.copy(alpha = 0.75f + 0.25f * pulse),
                )
            }
        }
    }
}
