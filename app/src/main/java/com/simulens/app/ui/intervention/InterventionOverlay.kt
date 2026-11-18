package com.simulens.app.ui.intervention

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.simulens.app.MainActivity
import com.simulens.app.data.tracker.InterventionActionReceiver
import com.simulens.app.data.tracker.InterventionExtras
import com.simulens.app.domain.rules.intervention.InterventionType
import com.simulens.app.ui.components.AppIcon
import com.simulens.app.ui.components.SquigglyDoodle
import com.simulens.app.ui.components.TapeDoodle
import com.simulens.app.ui.theme.*

object InterventionOverlay {

    private var overlayView: ComposeView? = null
    private var owner: OverlayOwner? = null

    fun canShow(context: Context) = Settings.canDrawOverlays(context)

    fun permissionIntent(context: Context) = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun isSnoozed(context: Context) = InterventionActionReceiver.isSnoozed(context)
    private fun snooze(context: Context) = InterventionActionReceiver.snooze(context)

    /** Main thread only. Returns false if it could not be shown. */
    fun show(
        context: Context,
        nudge: NudgePreview,
        respectSnooze: Boolean = true,
        onResult: ((OverlayAction) -> Unit)? = null
    ): Boolean {
        val appContext = context.applicationContext
        if (!canShow(appContext) || overlayView != null) return false
        if (respectSnooze && isSnoozed(appContext)) return false

        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lifecycleOwner = OverlayOwner().also { it.start() }

        fun finish(action: OverlayAction) {
            when (action) {
                OverlayAction.TAKE_BREAK -> goHome(appContext)
                OverlayAction.SNOOZE -> snooze(appContext)
                OverlayAction.LEARN_MORE -> openChatbot(appContext, nudge)
                OverlayAction.DISMISS -> {
                    InterventionActionReceiver.recordDismissal(appContext)
                }
            }
            remove(appContext)
            onResult?.invoke(action)
        }

        val view = ComposeView(appContext).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                SimulensTheme {
                    OverlayContent(nudge = nudge, onAction = ::finish)
                }
            }
        }

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurBehindRadius = 25
            }
        }

        return try {
            wm.addView(view, params)
            overlayView = view
            owner = lifecycleOwner
            true
        } catch (e: Exception) {
            lifecycleOwner.destroy()
            false
        }
    }

    fun remove(context: Context) {
        val wm = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView?.let { runCatching { wm.removeView(it) } }
        owner?.destroy()
        overlayView = null
        owner = null
    }

    private fun goHome(context: Context) {
        context.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun openChatbot(context: Context, nudge: NudgePreview) {
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(InterventionActionReceiver.EXTRA_OPEN_CHATBOT, true)
                putExtra(InterventionExtras.TYPE, nudge.reason.substringBefore(":"))
                putExtra(InterventionExtras.TITLE, nudge.title)
                putExtra(InterventionExtras.MESSAGE, nudge.message)
                putExtra(InterventionExtras.SOURCE_PACKAGE, nudge.packageName)
            }
        )
    }
}

enum class OverlayAction { TAKE_BREAK, SNOOZE, DISMISS, LEARN_MORE }

private data class TypeSpec(
    val icon: ImageVector,
    val fallbackTitle: String,
    val primaryButton: String
)

private fun specForType(type: InterventionType): TypeSpec = when (type) {
    InterventionType.SCHEDULE_CONFLICT -> TypeSpec(
        icon = Icons.Default.Shield,
        fallbackTitle = "You're in class",
        primaryButton = "Take a break"
    )
    InterventionType.SLEEP_WINDOW, InterventionType.WIND_DOWN, InterventionType.MORNING -> TypeSpec(
        icon = Icons.Rounded.Bedtime,
        fallbackTitle = "It's getting late",
        primaryButton = "Put the phone down"
    )
    InterventionType.HIGH_SESSION_DEVIATION, InterventionType.HIGH_DAILY_DEVIATION -> TypeSpec(
        icon = Icons.Rounded.Timer,
        fallbackTitle = "Time for a pause",
        primaryButton = "Take a short break"
    )
}

@Composable
private fun OverlayContent(nudge: NudgePreview, onAction: (OverlayAction) -> Unit) {
    val context = LocalContext.current
    val spec = remember(nudge.type) { specForType(nudge.type) }
    val isBreak = remember(nudge.packageName) {
        InterventionActionReceiver.isBreakActive(context, nudge.packageName)
    }
    val remainingBreakMins = remember(nudge.packageName) {
        InterventionActionReceiver.getRemainingBreakMins(context, nudge.packageName)
    }
    val originalBreakMins = remember(nudge.packageName) {
        InterventionActionReceiver.getOriginalBreakDurationMins(context, nudge.packageName)
    }

    var selectedBreakMins by remember { mutableLongStateOf(15L) }
    val breakOptions = listOf(5L, 15L, 30L, 60L)

    // Entrance Animation (Slide Up & Fade)
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val slideAnim by animateDpAsState(
        targetValue = if (visible) 0.dp else 110.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_slide"
    )

    val alphaAnim by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(350),
        label = "overlay_fade"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f * alphaAnim))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = slideAnim)
                .alpha(alphaAnim)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = InkSurface,
                border = BorderStroke(1.dp, InkBorder),
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { paneTitle = nudge.title }
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    // Header row: Type icon and squiggle doodle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(46.dp)
                                .background(InkSurfaceHigh, CircleShape)
                                .border(1.dp, InkBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = spec.icon,
                                contentDescription = null,
                                tint = IceBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        SquigglyDoodle(color = IceBlue, strokeWidth = 3.5f)
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = if (isBreak) "Break in progress" else nudge.title.ifBlank { spec.fallbackTitle },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = InkTextPrimary
                    )

                    Text(
                        text = if (isBreak) "$originalBreakMins-minute focus break active" else nudge.contextLine.ifBlank { "Focus protection active" },
                        style = MaterialTheme.typography.bodySmall,
                        color = InkTextSecondary
                    )

                    Spacer(Modifier.height(18.dp))

                    // App info card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = InkSurfaceHigh,
                        border = BorderStroke(1.dp, InkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(packageName = nudge.packageName, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = nudge.appName.ifBlank { "Monitored App" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = InkTextPrimary
                                )
                                Text(
                                    text = if (isBreak) "${nudge.appName} is locked ($remainingBreakMins min remaining)" else nudge.appSub.ifBlank { "Opened during focus session" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isBreak) InkAmber else InkTextSecondary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Body Message
                    Text(
                        text = if (isBreak) "Your $originalBreakMins-minute break isn't over yet. Take a few more minutes to rest away from ${nudge.appName}. ($remainingBreakMins min remaining)" else nudge.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkTextPrimary
                    )

                    // "Why this?" Real numbers line
                    if (nudge.whyThis.isNotBlank() && !isBreak) {
                        Spacer(Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = InkSurfaceHigh,
                            border = BorderStroke(1.dp, InkBorder)
                        ) {
                            Text(
                                text = "Why this? ${nudge.whyThis}",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkTextSecondary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }

                    val showBreakSelector = !isBreak && nudge.type != InterventionType.SLEEP_WINDOW &&
                            nudge.type != InterventionType.WIND_DOWN && nudge.type != InterventionType.MORNING

                    // Break Duration Selector
                    if (showBreakSelector) {
                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = "Choose break duration:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = InkTextSecondary
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            breakOptions.forEach { mins ->
                                val selected = selectedBreakMins == mins
                                Surface(
                                    onClick = { selectedBreakMins = mins },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) InkAmber else InkSurfaceHigh,
                                    border = BorderStroke(1.dp, if (selected) InkAmber else InkBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "${mins}m",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) InkOnAmber else InkTextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(22.dp))

                    val primaryButtonText = when {
                        isBreak -> "Return to home screen"
                        showBreakSelector -> "Take a ${selectedBreakMins}-min break"
                        else -> spec.primaryButton
                    }

                    // Primary CTA (Golden Amber button)
                    Button(
                        onClick = {
                            if (!isBreak) {
                                InterventionActionReceiver.startBreak(context, nudge.packageName, selectedBreakMins)
                            }
                            onAction(OverlayAction.TAKE_BREAK)
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InkAmber,
                            contentColor = InkOnAmber
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = primaryButtonText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Secondary CTA (Chat with Sidekick)
                    Surface(
                        onClick = { onAction(OverlayAction.LEARN_MORE) },
                        shape = RoundedCornerShape(16.dp),
                        color = InkSurfaceHigh,
                        border = BorderStroke(1.dp, IceBlue.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                tint = IceBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Chat with Buddy",
                                color = IceBlue,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Bottom dismiss and snooze actions (Only shown when NOT on active break!)
                    if (!isBreak) {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onAction(OverlayAction.SNOOZE) }) {
                                Text(
                                    text = "Snooze ${InterventionActionReceiver.SNOOZE_MINUTES} min",
                                    color = InkTextSecondary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            TextButton(onClick = { onAction(OverlayAction.DISMISS) }) {
                                Text(
                                    text = "Not now",
                                    color = InkTextSecondary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }

                TapeDoodle(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-8).dp)
                        .rotate(-4f)
                )
            }
        }
    }
}

private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun start() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
