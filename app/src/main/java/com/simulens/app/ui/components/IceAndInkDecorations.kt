package com.simulens.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simulens.app.ui.theme.*

/**
 * Playful highlighter underline modifier for title emphasis.
 */
fun Modifier.highlighter(color: Color = IceBlue.copy(alpha = 0.45f)) =
    drawBehind {
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, size.height * 0.58f),
            size = Size(size.width, size.height * 0.38f),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
    }

/**
 * Translucent masking tape strip doodle attached across card edges.
 */
@Composable
fun TapeDoodle(
    modifier: Modifier = Modifier,
    color: Color = Color(0x4093C5FD)
) {
    Box(
        modifier = modifier
            .width(52.dp)
            .height(16.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color)
            .border(0.5.dp, Color(0x66FFFFFF), RoundedCornerShape(3.dp))
    )
}

/**
 * Paperclip doodle pinned onto card edges.
 */
@Composable
fun PaperclipDoodle(
    modifier: Modifier = Modifier,
    color: Color = IceBlueLight
) {
    Canvas(
        modifier = modifier
            .width(18.dp)
            .height(34.dp)
    ) {
        val stroke = 3.5f
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(w * 0.35f, h * 0.25f)
            lineTo(w * 0.35f, h * 0.75f)
            cubicTo(w * 0.35f, h * 0.95f, w * 0.85f, h * 0.95f, w * 0.85f, h * 0.75f)
            lineTo(w * 0.85f, h * 0.18f)
            cubicTo(w * 0.85f, 0f, w * 0.15f, 0f, w * 0.15f, h * 0.18f)
            lineTo(w * 0.15f, h * 0.6f)
            cubicTo(w * 0.15f, h * 0.8f, w * 0.6f, h * 0.8f, w * 0.6f, h * 0.6f)
            lineTo(w * 0.6f, h * 0.35f)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Hand-drawn pencil doodle for headers.
 */
@Composable
fun PencilDoodle(
    modifier: Modifier = Modifier,
    color: Color = InkAmber
) {
    Canvas(
        modifier = modifier
            .width(22.dp)
            .height(22.dp)
    ) {
        val stroke = 2.5f
        val path = Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.8f)
            lineTo(size.width * 0.75f, size.height * 0.25f)
            lineTo(size.width * 0.85f, size.height * 0.35f)
            lineTo(size.width * 0.3f, size.height * 0.9f)
            close()

            moveTo(size.width * 0.2f, size.height * 0.8f)
            lineTo(size.width * 0.08f, size.height * 0.95f)
            lineTo(size.width * 0.3f, size.height * 0.9f)
            close()
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Playful hand-drawn squiggle wave seen across the "Option 1 · Ice and Ink" headers.
 */
@Composable
fun SquigglyDoodle(
    modifier: Modifier = Modifier,
    color: Color = IceBlue,
    strokeWidth: Float = 4f,
    waves: Int = 3
) {
    Canvas(
        modifier = modifier
            .width(60.dp)
            .height(16.dp)
    ) {
        val path = Path()
        val width = size.width
        val height = size.height
        val waveWidth = width / waves
        val midY = height / 2f
        val amplitude = height * 0.4f

        path.moveTo(0f, midY)
        for (i in 0 until waves) {
            val startX = i * waveWidth
            val cp1X = startX + waveWidth * 0.25f
            val cp1Y = midY - amplitude
            val cp2X = startX + waveWidth * 0.75f
            val cp2Y = midY + amplitude
            val endX = startX + waveWidth
            val endY = midY
            path.cubicTo(cp1X, cp1Y, cp2X, cp2Y, endX, endY)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/**
 * Golden amber avatar circle with user's initial.
 */
@Composable
fun InkAvatar(
    initial: String = "S",
    size: Dp = 38.dp,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(InkAmber)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial.take(1).uppercase(),
            color = InkOnAmber,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.48f).sp
        )
    }
}

/**
 * Sky-blue pill card for active session/class ("In class · 1h 17m left ...").
 */
@Composable
fun ActiveSessionBanner(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(22.dp),
        color = IceBlueBanner,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(InkOnAmber, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * "Highlighter S" Brand Mark Logo Composable:
 * Bold white "S" with translucent blue highlighter swipe behind it, and warm amber accent.
 */
@Composable
fun HighlighterSLogo(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val scale = w / 108f

        // 1. Translucent Ice Blue Highlighter Swipe (central safe zone)
        val swipePath = Path().apply {
            val left = 24f * scale
            val top = 50f * scale
            val right = 84f * scale
            val bottom = 66f * scale
            val rx = 7f * scale
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = left, top = top, right = right, bottom = bottom,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(rx, rx)
                )
            )
        }
        drawPath(
            path = swipePath,
            color = IceBlue.copy(alpha = 0.55f)
        )

        // 2. Bold Rounded "S" Mark in White
        val sPath = Path().apply {
            moveTo(63f * scale, 38f * scale)
            cubicTo(63f * scale, 32.5f * scale, 58.5f * scale, 28.5f * scale, 52f * scale, 28.5f * scale)
            cubicTo(45.5f * scale, 28.5f * scale, 41f * scale, 32.5f * scale, 41f * scale, 38f * scale)
            cubicTo(41f * scale, 47f * scale, 65f * scale, 45.5f * scale, 65f * scale, 56.5f * scale)
            cubicTo(65f * scale, 63.5f * scale, 59.5f * scale, 68f * scale, 50f * scale, 68f * scale)
            cubicTo(41.5f * scale, 68f * scale, 37f * scale, 62.5f * scale, 37f * scale, 56.5f * scale)
        }
        drawPath(
            path = sPath,
            color = Color.White,
            style = Stroke(width = 8.5f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 3. Amber Accent Dot
        drawCircle(
            color = InkAmber,
            radius = 3.5f * scale,
            center = Offset(71.5f * scale, 26f * scale)
        )
    }
}
