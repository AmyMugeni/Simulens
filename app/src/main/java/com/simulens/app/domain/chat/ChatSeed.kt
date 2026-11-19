package com.simulens.app.domain.chat

import com.simulens.app.domain.rules.intervention.InterventionType

/**
 * What the chat needs to open a conversation from a nudge or diagnostic card.
 * Everything here stays on the device: the opener is generated locally, and only text the user
 * taps or types is ever sent to the server.
 */
data class ChatSeed(
    /** Unique per nudge or diagnostic card. */
    val id: String,
    val type: InterventionType?,
    val appName: String = "",
    val packageName: String = "",
    /** Class subject, only for SCHEDULE_CONFLICT nudges. */
    val className: String? = null,
    /** How many times the app was opened in that class today. */
    val openCount: Int? = null,
    /** Self-reported BSMAS questionnaire score (e.g. 21 / 30). */
    val bsmasScore: Int? = null,
    /** ML Behavioral Risk probability score (e.g. 0.72 for 72%). */
    val riskProbability: Double? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class SeedGroup { CLASS, SLEEP, SESSION, MORNING, BSMAS_HIGH, RISK_ELEVATED }

/** A tappable reply. [query] is what is shown as the user's message and sent to the RAG pipeline. */
data class SeedChip(val label: String, val query: String)

data class ChatOpener(val text: String, val chips: List<SeedChip>)

object ChatSeedBuilder {

    fun groupOf(seed: ChatSeed): SeedGroup = when {
        seed.bsmasScore != null -> SeedGroup.BSMAS_HIGH
        seed.riskProbability != null -> SeedGroup.RISK_ELEVATED
        seed.type == InterventionType.SCHEDULE_CONFLICT -> SeedGroup.CLASS
        seed.type == InterventionType.SLEEP_WINDOW || seed.type == InterventionType.WIND_DOWN -> SeedGroup.SLEEP
        seed.type == InterventionType.MORNING -> SeedGroup.MORNING
        else -> SeedGroup.SESSION
    }

    /** Built from structured fields only, never from free-text messages. */
    fun opener(seed: ChatSeed): ChatOpener {
        val app = seed.appName.ifBlank { "monitored apps" }
        return when (groupOf(seed)) {
            SeedGroup.BSMAS_HIGH -> ChatOpener(
                text = "I noticed your self-reported assessment indicates higher concern (${seed.bsmasScore ?: 19}/30). " +
                        "No judgment — how you feel is what matters. What aspect of your social media usage feels most challenging right now?",
                chips = BSMAS_CHIPS
            )
            SeedGroup.RISK_ELEVATED -> ChatOpener(
                text = "Your 28-day behavioral check shows signs of heavier usage (${((seed.riskProbability ?: 0.5) * 100).toInt()}% risk estimate). " +
                        "This is an estimate to reflect on, not a diagnosis. What was going on during your recent sessions?",
                chips = RISK_CHIPS
            )
            SeedGroup.CLASS -> ChatOpener(
                text = "I noticed ${classPhrase(app, seed.className, seed.openCount)} " +
                        "No judgment. What was going on right before you reached for it?",
                chips = CLASS_CHIPS
            )
            SeedGroup.SLEEP -> ChatOpener(
                text = "It's late and you're still on $app. What's keeping you up tonight?",
                chips = SLEEP_CHIPS
            )
            SeedGroup.MORNING -> ChatOpener(
                text = "You opened $app soon after waking. What were you hoping to find?",
                chips = MORNING_CHIPS
            )
            SeedGroup.SESSION -> ChatOpener(
                text = "That's a long stretch on $app. What were you looking for when you started?",
                chips = SESSION_CHIPS
            )
        }
    }

    private fun classPhrase(app: String, className: String?, count: Int?): String {
        val where = className?.takeIf { it.isNotBlank() } ?: "class"
        return when {
            count == null || count < 1 -> "$app opened during $where today."
            count == 1 -> "$app opened once in $where today."
            else -> "$app opened $count times in $where today."
        }
    }

    private val BSMAS_CHIPS = listOf(
        SeedChip("Why is my score high?", "Why does my BSMAS self-assessment score indicate higher concern, and what habits contribute to it?"),
        SeedChip("Compulsive urge", "I feel a strong urge or preoccupation to check social media throughout the day. How can I regain control?"),
        SeedChip("Stress & Mood", "I find myself turning to social media when stressed or seeking escape. What are healthier coping strategies?"),
        SeedChip("Study & Focus", "How does social media preoccupation impact my academic focus, and how can I set better boundaries?")
    )

    private val RISK_CHIPS = listOf(
        SeedChip("What signals triggered this?", "What specific phone usage signals caused my behavioral risk check to show elevated risk?"),
        SeedChip("Late-night usage", "I've been spending time on my phone late at night. How can I protect my sleep schedule?"),
        SeedChip("Class distraction", "I'm opening apps during lecture hours. How can I stay present during my classes?"),
        SeedChip("Help me lower my risk", "What actionable steps can I take to reduce my screen time and lower my behavioral risk level?")
    )

    private val CLASS_CHIPS = listOf(
        SeedChip("Felt bored", "I felt bored in class and reached for my phone. What can I do instead?"),
        SeedChip("Waiting on a message", "I kept checking my phone in class while waiting on a message. How can I handle that?"),
        SeedChip("Deadline stress", "I reached for my phone in class because of deadline stress. What helps?"),
        SeedChip("Why does this affect my studies?", "How does using social media during class affect my studies and learning?")
    )

    private val SLEEP_CHIPS = listOf(
        SeedChip("Can't sleep", "I can't sleep and end up scrolling on my phone at night. What can I do?"),
        SeedChip("Winding down", "I use my phone to wind down before bed. Is that okay for my sleep, and what are better options?"),
        SeedChip("One more video", "I keep watching one more video at night. How do I stop?"),
        SeedChip("How does this affect my sleep?", "How does social media use at night affect sleep?")
    )

    private val SESSION_CHIPS = listOf(
        SeedChip("Lost track of time", "I lose track of time on social media. Why does that happen and how can I stop?"),
        SeedChip("Needed a break", "I use social media as a break from work. What are healthier breaks?"),
        SeedChip("Procrastinating", "I use social media to procrastinate. How do I stop?"),
        SeedChip("Help me plan a break", "Help me plan a healthy break from social media that actually recharges me.")
    )

    private val MORNING_CHIPS = listOf(
        SeedChip("Habit", "I reach for social media out of habit right after waking. How do I break that habit?"),
        SeedChip("Checking messages", "I check social media for messages as soon as I wake up. Is that a problem and what can I do?"),
        SeedChip("Bored", "I scroll social media when I wake up out of boredom. What could I do instead?"),
        SeedChip("Why does a morning scroll matter?", "Why does scrolling social media first thing in the morning matter?")
    )
}