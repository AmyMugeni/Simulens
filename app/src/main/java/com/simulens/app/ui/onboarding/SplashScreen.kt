package com.simulens.app.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.simulens.app.ui.components.GradientButton
import com.simulens.app.ui.components.HighlighterSLogo
import com.simulens.app.ui.components.PaperclipDoodle
import com.simulens.app.ui.components.PencilDoodle
import com.simulens.app.ui.components.PillBadge
import com.simulens.app.ui.components.SquigglyDoodle
import com.simulens.app.ui.components.TapeDoodle
import com.simulens.app.ui.theme.*
import kotlinx.coroutines.delay

fun Modifier.animatedHighlighter(
    progress: Float,
    color: Color = IceBlue.copy(alpha = 0.45f)
) = drawBehind {
    val highlightWidth = size.width * progress.coerceIn(0f, 1f)
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, size.height * 0.58f),
        size = Size(highlightWidth, size.height * 0.38f),
        cornerRadius = CornerRadius(4.dp.toPx())
    )
}

@Composable
fun SplashScreen(
    viewModel: OnboardingViewModel,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val isBsmasCompleted by viewModel.isBsmasCompleted.collectAsState()

    val isProfileComplete = remember(userProfile, isBsmasCompleted) {
        viewModel.calculateCompletionPercentage() >= 1.0f
    }

    // Entrance Animations
    var startAnim by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        startAnim = true
    }

    val logoBounceScale by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0.2f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logo_bounce"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(900, delayMillis = 300),
        label = "content_fade"
    )

    val cardOffsetY by animateDpAsState(
        targetValue = if (startAnim) 0.dp else 45.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_slide"
    )

    val highlightProgress by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(1200, delayMillis = 500, easing = FastOutSlowInEasing),
        label = "highlight_sweep"
    )

    // Interactive Tap Animation on Logo
    var logoTapped by remember { mutableStateOf(false) }
    val tapRotation by animateFloatAsState(
        targetValue = if (logoTapped) 360f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        finishedListener = { logoTapped = false },
        label = "tap_rotation"
    )

    // Continuous Ambient Animations
    val infiniteTransition = rememberInfiniteTransition(label = "splash_infinite")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val doodleRotate by infiniteTransition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "doodle_rotate"
    )

    val floatOffset1 by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_1"
    )

    val floatOffset2 by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_2"
    )

    LaunchedEffect(isProfileComplete) {
        if (isProfileComplete) {
            delay(2400)
            onNavigateToDashboard()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(InkBackground)
    ) {
        // Ambient Radial Glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            IceBlue.copy(alpha = 0.22f),
                            InkSurface.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Floating Playful Doodles in Background
        PencilDoodle(
            color = InkAmber,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 32.dp, y = (110 + floatOffset1).dp)
                .rotate(doodleRotate - 15f)
                .alpha(contentAlpha * 0.85f)
        )

        PaperclipDoodle(
            color = IceBlueLight,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-32).dp, y = (120 + floatOffset2).dp)
                .rotate(doodleRotate + 22f)
                .alpha(contentAlpha * 0.85f)
        )

        SquigglyDoodle(
            color = IceBlue.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 18.dp, y = (-120 + floatOffset2).dp)
                .alpha(contentAlpha * 0.7f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Interactive "Highlighter S" Brand Mark Logo
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .scale(logoBounceScale * pulseScale)
                    .rotate(tapRotation)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        logoTapped = true
                    }
            ) {
                // Ambient backing ring
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(IceBlueContainer.copy(alpha = 0.4f), CircleShape)
                )

                HighlighterSLogo(size = 130.dp)
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Animated Wordmark & Titles
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(contentAlpha)
                    .offset(y = cardOffsetY)
            ) {
                AnimatedContent(
                    targetState = isProfileComplete,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(800)) togetherWith fadeOut(animationSpec = tween(600))
                    },
                    label = "text_animation"
                ) { complete ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (complete) {
                            Text(
                                text = "Welcome back,",
                                style = MaterialTheme.typography.titleLarge,
                                color = InkTextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = userProfile.username.ifBlank { "Student" },
                                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                                color = InkAmber,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.animatedHighlighter(
                                    progress = highlightProgress,
                                    color = InkAmber.copy(alpha = 0.35f)
                                )
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Simulens",
                                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                                    color = InkTextPrimary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.animatedHighlighter(progress = highlightProgress)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = InkAmber,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .rotate(doodleRotate * 2f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                PillBadge(
                    text = "Focus • Balance • Well-Being",
                    containerColor = IceBlueContainer,
                    contentColor = IceBlueLight
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Privacy Card / Progress Indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(contentAlpha)
                    .offset(y = cardOffsetY)
            ) {
                if (!isProfileComplete) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = InkSurface),
                            border = BorderStroke(1.dp, InkBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(IceBlueContainer, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Shield,
                                        contentDescription = "Privacy Shield",
                                        tint = IceBlue,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column {
                                    Text(
                                        text = "Purely On-Device Privacy",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = InkTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Simulens evaluates usage patterns 100% on your device. Zero telemetry leaves your phone.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = InkTextSecondary
                                    )
                                }
                            }
                        }

                        TapeDoodle(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-16).dp, y = (-8).dp)
                                .rotate(5f + doodleRotate * 0.5f)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                            color = IceBlue
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Loading your personalized dashboard...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = IceBlueLight
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (!isProfileComplete) {
                GradientButton(
                    text = "Get Started",
                    onClick = onNavigateToOnboarding,
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(contentAlpha)
                        .offset(y = cardOffsetY)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}