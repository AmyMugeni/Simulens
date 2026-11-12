package com.simulens.app.ui.intervention

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.simulens.app.ui.components.TapeDoodle
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
import com.simulens.app.ui.components.AppIcon
import com.simulens.app.ui.components.SquigglyDoodle
import com.simulens.app.data.tracker.InterventionActionReceiver
import com.simulens.app.data.tracker.InterventionExtras
import com.simulens.app.ui.theme.*

object InterventionOverlay {

    private var overlayView: ComposeView? = null
    private var owner: OverlayOwner? = null

    fun canShow(context: Context) = Settings.canDrawOverlays(context)

    fun permissionIntent(context: Context) = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // One snooze store: the receiver's.
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
                OverlayAction.DISMISS -> Unit
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

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )

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

@Composable
private fun OverlayContent(nudge: NudgePreview, onAction: (OverlayAction) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = InkSurface,
                border = BorderStroke(1.dp, InkBorder),
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                // Header decoration and shield
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
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = IceBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    SquigglyDoodle(color = IceBlue, strokeWidth = 3.5f)
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = nudge.title.ifBlank { "You're in class" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = InkTextPrimary
                )

                Text(
                    text = "Focus protection active",
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
                                text = "Opened during focus session",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkTextSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Body Message
                Text(
                    text = nudge.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkTextPrimary,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                )

                Spacer(Modifier.height(22.dp))

                // Primary CTA (Golden Amber button)
                Button(
                    onClick = { onAction(OverlayAction.TAKE_BREAK) },
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
                        text = "Take a 5-min break",
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
                            text = "Chat with Sidekick",
                            color = IceBlue,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Bottom dismiss and snooze actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { onAction(OverlayAction.SNOOZE) }) {
                        Text(
                            text = "Snooze 15 min",
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
