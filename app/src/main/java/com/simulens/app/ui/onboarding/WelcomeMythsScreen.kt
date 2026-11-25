package com.simulens.app.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simulens.app.ui.components.*
import com.simulens.app.ui.theme.*

/**
 * First onboarding screen: establishes Simulens as a safe space by debunking
 * the myth of "personal willpower failure" and revealing how persuasive algorithms operate.
 */
@Composable
fun WelcomeMythsScreen(
    onNavigateNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InkBackground)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle Tag
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = InkAmber,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Welcome to Simulens",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = InkAmber
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Compassionate Headline
            Text(
                text = "Restore your digital health.",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = InkTextPrimary,
                modifier = Modifier.highlighter()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Social feeds aren't neutral tools. They are precision-engineered to capture your attention. Here is what the behavioral research actually reveals:",
                style = MaterialTheme.typography.bodyMedium,
                color = InkTextSecondary,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Magnifying Guy Vector Illustration
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                MagnifyingGuyIllustration(size = 190.dp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Fact 1: Persuasive Tech & Dopamine
            MythFactCard(
                icon = Icons.Default.Psychology,
                myth = "Myth: \"I just lack discipline and self-control.\"",
                factTitle = "Persuasive Tech & Variable Rewards",
                factBody = "Apps are designed like casino slot machines to trick your brain into checking your phone on autopilot, without giving you a clear place to stop and reflect.",
                accentColor = IceBlue
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Fact 2: Academic Performance & Attention Residue
            MythFactCard(
                icon = Icons.Default.MenuBook,
                myth = "Myth: \"A 10-second check during class won't hurt.\"",
                factTitle = "Attention Residue & Lecture Retention",
                factBody = "Taking just a quick peek at your phone during class breaks your concentration for a long time, making it much harder to understand and remember the lesson for your exams",
                accentColor = InkAmber
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Fact 3: Sleep Architecture & Blue Light
            MythFactCard(
                icon = Icons.Default.Nightlight,
                myth = "Myth: \"Scrolling helps me relax and fall asleep.\"",
                factTitle = "Circadian Rhythm & Sleep Delay",
                factBody = "Watching quick, endlessly scrolling videos before bed tricks your brain into staying alert and delays your natural sleep cycle, leaving you tired the next day.",
                accentColor = IceBlueLight
            )

            Spacer(modifier = Modifier.height(28.dp))
        }

        // Bottom Navigation Bar
        Surface(
            color = InkSurfaceLow,
            border = BorderStroke(1.dp, InkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GradientButton(
                    text = "See How Simulens Helps",
                    onClick = onNavigateNext,
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun MythFactCard(
    icon: ImageVector,
    myth: String,
    factTitle: String,
    factBody: String,
    accentColor: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = InkSurface),
        border = BorderStroke(1.dp, InkBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Myth Header Tag
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = RiskHighBg,
                    border = BorderStroke(1.dp, RiskHigh.copy(alpha = 0.5f)),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "✕",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = RiskHigh
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = myth,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = InkTextMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = InkBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Reality / Scientific Fact
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.18f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = factTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = InkTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = factBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkTextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
