package com.simulens.app.rag

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import com.simulens.app.data.tracker.InterventionExtras
import com.simulens.app.domain.chat.ChatSeed
import com.simulens.app.domain.rules.intervention.InterventionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Activity-scoped hand-over between the launch intent and the chat screen. It survives rotation,
 * and each nudge id is accepted only once.
 */
class ChatLaunchViewModel : ViewModel() {
    private val _pending = MutableStateFlow<ChatSeed?>(null)
    val pending: StateFlow<ChatSeed?> = _pending

    private val seen = HashSet<String>()

    fun offer(seed: ChatSeed): Boolean {
        if (!seen.add(seed.id)) return false
        _pending.value = seed
        return true
    }

    fun consume(id: String) {
        if (_pending.value?.id == id) _pending.value = null
    }

    /** A seed held back by onboarding must not pop up long after the nudge. */
    fun dropIfStale(nowMs: Long = System.currentTimeMillis()) {
        val s = _pending.value ?: return
        if (nowMs - s.createdAt > SEED_TTL_MS) _pending.value = null
    }

    private companion object {
        const val SEED_TTL_MS = 2 * 60_000L
    }
}

object ChatSeedIntent {
    /** Pure read of the extras; the caller removes them so a recreate does not replay them. */
    fun from(context: Context, i: Intent): ChatSeed {
        val pkg = i.getStringExtra(InterventionExtras.SOURCE_PACKAGE).orEmpty()
        val appName = i.getStringExtra(InterventionExtras.APP_NAME)?.takeIf { it.isNotBlank() }
            ?: labelOf(context, pkg)
        return ChatSeed(
            id = i.getStringExtra(InterventionExtras.NUDGE_ID) ?: UUID.randomUUID().toString(),
            type = runCatching {
                InterventionType.valueOf(i.getStringExtra(InterventionExtras.TYPE).orEmpty())
            }.getOrNull(),
            appName = appName,
            packageName = pkg,
            className = i.getStringExtra(InterventionExtras.CLASS_NAME)?.takeIf { it.isNotBlank() },
            openCount = i.getIntExtra(InterventionExtras.OPEN_COUNT, 0).takeIf { it > 0 }
        )
    }

    private fun labelOf(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault("")
}
