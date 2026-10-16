package com.cognilens.app.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cognilens.app.data.tracker.AppUsageTrackerService
import com.cognilens.app.data.tracker.PermissionUtils
import com.cognilens.app.ui.components.GradientButton
import com.cognilens.app.ui.components.PillBadge

@Composable
fun PermissionsScreen(
    onNavigateNext: () -> Unit
) {
    val context = LocalContext.current
    var hasUsagePermission by remember { mutableStateOf(PermissionUtils.hasUsageStatsPermission(context)) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Grant System Access",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "CogniLens needs system access to detect compulsive app launches and display real-time focus interventions.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Permission Card 1: Usage Access
        PermissionCard(
            step = 1,
            title = "Usage Access",
            description = "Detects background app usage during scheduled lecture hours.",
            isGranted = hasUsagePermission,
            onRequest = {
                PermissionUtils.openUsageStatsSettings(context)
                hasUsagePermission = PermissionUtils.hasUsageStatsPermission(context)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Permission Card 2: Display Over Apps
        PermissionCard(
            step = 2,
            title = "Display Over Apps",
            description = "Displays micro-interventions when distraction velocity spikes.",
            isGranted = hasOverlayPermission,
            onRequest = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                hasOverlayPermission = Settings.canDrawOverlays(context)
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        if (!hasUsagePermission) {
            Text(
                text = "Usage Access is required to continue.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        }

        GradientButton(
            text = "Continue",
            onClick = {
                if (hasUsagePermission) {
                    val serviceIntent = Intent(context, AppUsageTrackerService::class.java)
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
                onNavigateNext()
            },
            enabled = hasUsagePermission,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun PermissionCard(
    step: Int,
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    val containerColor = if (isGranted) {
        colors.primaryContainer.copy(alpha = 0.5f)
    } else {
        colors.surfaceContainerLow
    }

    val borderColor = if (isGranted) {
        colors.primary
    } else {
        colors.outlineVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(width = if (isGranted) 1.5.dp else 1.dp, color = borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (isGranted) colors.primary else colors.secondaryContainer,
                            shape = CircleShape
                        )
                ) {
                    if (isGranted) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Permission Granted",
                            tint = colors.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = step.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )

                if (isGranted) {
                    PillBadge(
                        text = "Granted",
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )

            if (!isGranted) {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onRequest,
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer
                    )
                ) {
                    Text(
                        text = "Enable Access",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
