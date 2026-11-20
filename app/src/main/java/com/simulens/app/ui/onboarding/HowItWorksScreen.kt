package com.simulens.app.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
 * Second onboarding screen: clarifies what Simulens is (a supportive companion,
 * not a punitive locker) and explains how intentional micro-friction breaks compulsive loops.
 */
@Composable
fun HowItWorksScreen(
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
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = IceBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "A Companion • Not a Prison",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = IceBlue
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Headline
            Text(
                text = "Breaking the Loop Together.",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = InkTextPrimary,
                modifier = Modifier.highlighter()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Most blockers lock you out with harsh restrictions that provoke anxiety. Simulens introduces gentle, scientifically grounded micro-friction instead:",
                style = MaterialTheme.typography.bodyMedium,
                color = InkTextSecondary,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Pillar 1: Non-punitive Nudges
            ValuePillarCard(
                icon = Icons.Default.NotificationsActive,
                badge = "AWARENESS OVER LOCKS",
                title = "Gentle, Real-Time Context Nudges",
                description = "When you open a distracting app during class or past bedtime, Simulens gently taps your shoulder. You can take a 15-minute break or snooze with no judgment.",
                accentColor = IceBlue
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Pillar 2: Evidence-based Sidekick Chat
            ValuePillarCard(
                icon = Icons.Default.Chat,
                badge = "EVIDENCE-BASED AI",
                title = "Grounded Research Companion",
                description = "Chat with the Simulens Sidekick directly from any nudge. Ask \"Why does scrolling affect my studies?\" and receive cited answers drawn from scientific wellbeing literature.",
                accentColor = InkAmber
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Pillar 3: Pure Local Privacy
            ValuePillarCard(
                icon = Icons.Default.Lock,
                badge = "100% PRIVATE & ON-DEVICE",
                title = "Zero Data Extraction",
                description = "Your usage timestamps, schedules, and app launch counts stay strictly on your phone. Nothing leaves this device without your explicit opt-in confirmation.",
                accentColor = RiskLow
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
                    text = "Configure My Protections",
                    onClick = onNavigateNext,
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ValuePillarCard(
    icon: ImageVector,
    badge: String,
    title: String,
    description: String,
    accentColor: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = InkSurface),
        border = BorderStroke(1.dp, InkBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.18f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                PillBadge(
                    text = badge,
                    containerColor = accentColor.copy(alpha = 0.15f),
                    contentColor = accentColor
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = InkTextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = InkTextSecondary,
                lineHeight = 20.sp
            )
        }
    }
}
