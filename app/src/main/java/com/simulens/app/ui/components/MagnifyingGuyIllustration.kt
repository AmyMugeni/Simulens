package com.simulens.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simulens.app.ui.theme.*

/**
 * Playful, hand-drawn vector illustration of an ink-and-ice student investigator
 * holding a glowing magnifying glass over a phone screen with sparkle accents.
 */
@Composable
fun MagnifyingGuyIllustration(
    modifier: Modifier = Modifier,
    size: Dp = 180.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "magnifying_guy")

    // Subtle gentle breathing/floating motion
    val floatY by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_y"
    )

    // Magnifying glass gentle angle sway
    val glassAngle by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glass_sway"
    )

    // Sparkle pulse
    val sparkleScale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkle_scale"
    )

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val scale = w / 200f
        val offsetY = floatY * scale

        // 1. Ambient Soft Glow behind phone and glass
        drawCircle(
            color = IceBlue.copy(alpha = 0.12f),
            radius = 65f * scale,
            center = Offset(110f * scale, 105f * scale + offsetY)
        )

        // 2. Character Body & Hoodie (Minimalist Ink outline style)
        val bodyColor = InkSurfaceHigh
        val strokeOutline = Stroke(width = 3.5f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val strokeDetail = Stroke(width = 2.5f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Torso/Hoodie
        val hoodiePath = Path().apply {
            moveTo(35f * scale, 185f * scale + offsetY)
            cubicTo(
                45f * scale, 140f * scale + offsetY,
                65f * scale, 130f * scale + offsetY,
                82f * scale, 132f * scale + offsetY
            )
            lineTo(98f * scale, 138f * scale + offsetY)
            cubicTo(
                115f * scale, 145f * scale + offsetY,
                128f * scale, 160f * scale + offsetY,
                135f * scale, 185f * scale + offsetY
            )
        }
        drawPath(hoodiePath, color = bodyColor)
        drawPath(hoodiePath, color = IceBlueLight.copy(alpha = 0.8f), style = strokeOutline)

        // Head / Face
        drawCircle(
            color = InkSurfaceHighest,
            radius = 24f * scale,
            center = Offset(72f * scale, 108f * scale + offsetY)
        )
        drawCircle(
            color = IceBlueLight,
            radius = 24f * scale,
            center = Offset(72f * scale, 108f * scale + offsetY),
            style = strokeOutline
        )

        // Cap / Hair doodle
        val hairPath = Path().apply {
            moveTo(50f * scale, 105f * scale + offsetY)
            cubicTo(
                55f * scale, 84f * scale + offsetY,
                88f * scale, 84f * scale + offsetY,
                94f * scale, 102f * scale + offsetY
            )
            lineTo(98f * scale, 105f * scale + offsetY)
        }
        drawPath(hairPath, color = InkAmber, style = Stroke(width = 3.5f * scale, cap = StrokeCap.Round))

        // Curious Eye (focused rightward toward phone)
        drawCircle(
            color = Color.White,
            radius = 4.5f * scale,
            center = Offset(80f * scale, 107f * scale + offsetY)
        )
        drawCircle(
            color = InkBackground,
            radius = 2.2f * scale,
            center = Offset(82f * scale, 107f * scale + offsetY)
        )

        // Smile
        val smilePath = Path().apply {
            moveTo(76f * scale, 117f * scale + offsetY)
            quadraticTo(82f * scale, 122f * scale + offsetY, 88f * scale, 118f * scale + offsetY)
        }
        drawPath(smilePath, color = IceBlueLight, style = strokeDetail)

        // Arm reaching forward
        val armPath = Path().apply {
            moveTo(68f * scale, 142f * scale + offsetY)
            cubicTo(
                85f * scale, 155f * scale + offsetY,
                112f * scale, 150f * scale + offsetY,
                125f * scale, 138f * scale + offsetY
            )
        }
        drawPath(armPath, color = IceBlue, style = strokeOutline)

        // 3. Smartphone Device in Hand
        val phoneLeft = 135f * scale
        val phoneTop = 95f * scale + offsetY
        val phoneWidth = 36f * scale
        val phoneHeight = 65f * scale
        val phoneRadius = 8f * scale

        // Phone body
        drawRoundRect(
            color = InkSurfaceLow,
            topLeft = Offset(phoneLeft, phoneTop),
            size = androidx.compose.ui.geometry.Size(phoneWidth, phoneHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(phoneRadius, phoneRadius)
        )
        drawRoundRect(
            color = IceBlue.copy(alpha = 0.7f),
            topLeft = Offset(phoneLeft, phoneTop),
            size = androidx.compose.ui.geometry.Size(phoneWidth, phoneHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(phoneRadius, phoneRadius),
            style = strokeDetail
        )

        // Screen glowing feed blocks
        drawRoundRect(
            color = IceBlueBanner.copy(alpha = 0.5f),
            topLeft = Offset(phoneLeft + 5f * scale, phoneTop + 10f * scale),
            size = androidx.compose.ui.geometry.Size(phoneWidth - 10f * scale, 14f * scale),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale, 4f * scale)
        )
        drawRoundRect(
            color = InkAmber.copy(alpha = 0.6f),
            topLeft = Offset(phoneLeft + 5f * scale, phoneTop + 28f * scale),
            size = androidx.compose.ui.geometry.Size(phoneWidth - 14f * scale, 8f * scale),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * scale, 3f * scale)
        )
        drawRoundRect(
            color = IceBlueLight.copy(alpha = 0.4f),
            topLeft = Offset(phoneLeft + 5f * scale, phoneTop + 40f * scale),
            size = androidx.compose.ui.geometry.Size(phoneWidth - 10f * scale, 8f * scale),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * scale, 3f * scale)
        )

        // 4. Glowing Magnifying Glass hovering over the phone
        rotate(degrees = glassAngle, pivot = Offset(132f * scale, 110f * scale + offsetY)) {
            val lensCenter = Offset(124f * scale, 102f * scale + offsetY)
            val lensRadius = 26f * scale

            // Lens glass glow
            drawCircle(
                color = IceBlue.copy(alpha = 0.22f),
                radius = lensRadius,
                center = lensCenter
            )

            // Lens rim
            drawCircle(
                color = InkAmber,
                radius = lensRadius,
                center = lensCenter,
                style = Stroke(width = 4.5f * scale)
            )

            // Lens shine reflection curve
            val shinePath = Path().apply {
                moveTo(lensCenter.x - 16f * scale, lensCenter.y - 12f * scale)
                cubicTo(
                    lensCenter.x - 8f * scale, lensCenter.y - 20f * scale,
                    lensCenter.x + 8f * scale, lensCenter.y - 20f * scale,
                    lensCenter.x + 16f * scale, lensCenter.y - 12f * scale
                )
            }
            drawPath(shinePath, color = Color.White.copy(alpha = 0.7f), style = Stroke(width = 2.5f * scale, cap = StrokeCap.Round))

            // Magnifying Glass Handle
            val handleStart = Offset(lensCenter.x - 18f * scale, lensCenter.y + 18f * scale)
            val handleEnd = Offset(handleStart.x - 22f * scale, handleStart.y + 24f * scale)
            drawLine(
                color = InkAmberDark,
                start = handleStart,
                end = handleEnd,
                strokeWidth = 6f * scale,
                cap = StrokeCap.Round
            )
        }

        // 5. Whimsical Sparks / Discovery Stars
        drawSparkle(
            center = Offset(178f * scale, 68f * scale + offsetY),
            size = 14f * scale * sparkleScale,
            color = InkAmberLight
        )
        drawSparkle(
            center = Offset(105f * scale, 55f * scale + offsetY),
            size = 10f * scale * sparkleScale,
            color = IceBlueLight
        )
        drawSparkle(
            center = Offset(182f * scale, 135f * scale + offsetY),
            size = 9f * scale * (1.8f - sparkleScale),
            color = Color.White
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSparkle(
    center: Offset,
    size: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(center.x, center.y - size)
        quadraticTo(center.x, center.y, center.x + size, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + size)
        quadraticTo(center.x, center.y, center.x - size, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - size)
    }
    drawPath(path, color = color)
}
