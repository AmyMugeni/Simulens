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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.simulens.app.ui.components.AppIcon
import com.simulens.app.data.tracker.InterventionActionReceiver
import com.simulens.app.data.tracker.InterventionExtras
// import com.simulens.app.ui.theme.SimulensTheme                      // your theme


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
                // Wrap in your theme so colors match the app, e.g. SimulensTheme { ... }
                OverlayContent(nudge = nudge, onAction = ::finish)
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
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceContainerLow,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(64.dp).background(
                        Brush.linearGradient(listOf(colors.primary, colors.secondary)), CircleShape
                    )
                ) {
                    Icon(Icons.Default.Shield, null, tint = colors.onPrimary, modifier = Modifier.size(34.dp))
                }

                Spacer(Modifier.height(16.dp))
                Text(nudge.title, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, color = colors.onSurface, textAlign = TextAlign.Center)

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(packageName = nudge.packageName, size = 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(nudge.appName, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = colors.onSurface)
                }

                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.errorContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, colors.error.copy(alpha = 0.3f))
                ) {
                    Text(nudge.message, style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface, modifier = Modifier.padding(16.dp))
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onAction(OverlayAction.LEARN_MORE) }) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Learn More (Ask AI Chatbot)", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onAction(OverlayAction.TAKE_BREAK) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Accept (Take Break)", fontWeight = FontWeight.Bold) }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onAction(OverlayAction.SNOOZE) },
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                    ) { Text("Snooze 15m") }
                    OutlinedButton(
                        onClick = { onAction(OverlayAction.DISMISS) },
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                    ) { Text("Dismiss") }
                }
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