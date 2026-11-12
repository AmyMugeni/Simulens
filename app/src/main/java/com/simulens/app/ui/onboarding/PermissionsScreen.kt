package com.simulens.app.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.draw.rotate
import com.simulens.app.data.tracker.AppUsageTrackerService
import com.simulens.app.data.tracker.PermissionUtils
import com.simulens.app.ui.components.PaperclipDoodle
import com.simulens.app.ui.components.SquigglyDoodle
import com.simulens.app.ui.components.TapeDoodle
import com.simulens.app.ui.components.highlighter
import com.simulens.app.ui.theme.*

@Composable
fun PermissionsScreen(
    onNavigateNext: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasUsagePermission by remember { mutableStateOf(PermissionUtils.hasUsageStatsPermission(context)) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasNotificationPermission by remember { mutableStateOf(PermissionUtils.hasNotificationPermission(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsagePermission = PermissionUtils.hasUsageStatsPermission(context)
                hasOverlayPermission = Settings.canDrawOverlays(context)
                hasNotificationPermission = PermissionUtils.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted || PermissionUtils.hasNotificationPermission(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InkBackground)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // Header with Ice Blue Doodle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Setup Permissions",
                style = MaterialTheme.typography.labelLarge,
                color = IceBlue,
                fontWeight = FontWeight.Bold
            )
            SquigglyDoodle(color = IceBlue)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Grant System Access",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = InkTextPrimary,
            modifier = Modifier.highlighter()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Simulens needs system access to detect compulsive app launches and display real-time focus interventions.",
            style = MaterialTheme.typography.bodyLarge,
            color = InkTextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

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

        Spacer(modifier = Modifier.height(16.dp))

        // Permission Card 3: Real-Time Notifications
        PermissionCard(
            step = 3,
            title = "Real-Time Notifications",
            description = "Presents gentle nudges and boundary alerts while using social apps.",
            isGranted = hasNotificationPermission,
            onRequest = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    PermissionUtils.openNotificationSettings(context)
                    hasNotificationPermission = PermissionUtils.hasNotificationPermission(context)
                }
            }
        )

        Spacer(modifier = Modifier.height(28.dp))

        if (!hasUsagePermission) {
            Text(
                text = "Usage Access is required to continue.",
                style = MaterialTheme.typography.labelMedium,
                color = InkTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        }

        // Primary Golden Amber Continue Button
        Button(
            onClick = {
                if (hasUsagePermission) {
                    val serviceIntent = Intent(context, AppUsageTrackerService::class.java)
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
                onNavigateNext()
            },
            enabled = hasUsagePermission,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = InkAmber,
                contentColor = InkOnAmber,
                disabledContainerColor = InkSurfaceHigh,
                disabledContentColor = InkTextMuted
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Continue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

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
    val containerColor = if (isGranted) InkSurfaceHigh.copy(alpha = 0.8f) else InkSurface
    val borderColor = if (isGranted) RiskLow.copy(alpha = 0.5f) else InkBorder

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(width = 1.dp, color = borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = if (isGranted) RiskLow.copy(alpha = 0.2f) else InkSurfaceHigh,
                            shape = CircleShape
                        )
                ) {
                    if (isGranted) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Permission Granted",
                            tint = RiskLow,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = step.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = InkTextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = InkTextPrimary,
                    modifier = Modifier.weight(1f)
                )

                if (isGranted) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = RiskLow.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, RiskLow.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "Granted",
                            color = RiskLow,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = InkTextSecondary
            )


                if (!isGranted) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onRequest,
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InkSurfaceHigh,
                            contentColor = IceBlue
                        ),
                        border = BorderStroke(1.dp, IceBlue.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "Enable Access",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } // Closes Column

        } // ADD THIS: Closes Card

        // Doodles are now direct children of Box
        if (step == 1) {
            TapeDoodle(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-16).dp, y = (-8).dp)
                    .rotate(6f)
            )
        } else if (step == 2) {
            PaperclipDoodle(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-16).dp, y = (-10).dp)
                    .rotate(-12f)
            )
        }
    } // Closes Box
} // Closes PermissionCard
