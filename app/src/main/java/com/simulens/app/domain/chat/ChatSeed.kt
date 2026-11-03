package com.simulens.app.domain.chat

import com.simulens.app.domain.rules.intervention.InterventionType

/** Everything the chatbot needs to know about the nudge the student just received. */
data class ChatSeed(
    val type: String,          // InterventionType name, as sent in the intent extras
    val appLabel: String,
    val title: String,
    val message: String,       // already contains personal counts, e.g. "16th time this week"
    val packageName: String
)

object ChatSeedBuilder {

    private fun typeOf(seed: ChatSeed): InterventionType? =
        runCatching { InterventionType.valueOf(seed.type) }.getOrNull()

    /** Tappable prompts shown above the input box. At most four. */
    fun suggestions(seed: ChatSeed, strictModeAvailable: Boolean = false): List<String> {
        val app = seed.appLabel
        val list = when (typeOf(seed)) {
            InterventionType.SCHEDULE_CONFLICT -> listOf(
                "Why do I keep opening $app in class?",
                "Help me make a plan for class hours",
                if (strictModeAvailable) "What would strict mode change for me?"
                else "What can I do when I feel the urge?",
                "Is this a normal amount?"
            )
            InterventionType.SLEEP_WINDOW -> listOf(
                "Why is late-night scrolling so hard to stop?",
                "Help me set up a wind-down routine",
                "How much is this affecting my sleep?"
            )
            InterventionType.WIND_DOWN -> listOf(
                "What can I do instead in the last 30 minutes before bed?",
                "Why does scrolling before bed matter?",
                "Help me pick a cut-off time"
            )
            InterventionType.MORNING -> listOf(
                "How do I stop reaching for $app first thing?",
                "What's a better first half hour?",
                "Why does the morning matter?"
            )
            InterventionType.HIGH_SESSION_DEVIATION -> listOf(
                "Why did this visit run so long?",
                "How can I catch myself mid-scroll?",
                "Is a long visit always a bad sign?"
            )
            InterventionType.HIGH_DAILY_DEVIATION -> listOf(
                "Why is today heavier than usual?",
                "What can I do for the rest of today?",
                "Help me plan tomorrow"
            )
            else -> listOf("How can I use $app less?")
        }
        return (list + "Why did Simulens flag this?").take(4)
    }

    /** First assistant message, so the chat opens already knowing why the student is here. */
    fun opener(seed: ChatSeed): String {
        val app = seed.appLabel
        val hook = when (typeOf(seed)) {
            InterventionType.SCHEDULE_CONFLICT ->
                "Class hours are a hard time to resist, and noticing it is the first step."
            InterventionType.SLEEP_WINDOW, InterventionType.WIND_DOWN ->
                "Late-night scrolling is really easy to slip into."
            InterventionType.MORNING ->
                "Mornings set the tone for the day, so this one's worth a look."
            InterventionType.HIGH_SESSION_DEVIATION ->
                "Long visits sneak up on everyone."
            InterventionType.HIGH_DAILY_DEVIATION ->
                "Some days just run heavier than others."
            else -> "Happy to help."
        }
        return "Hey, I saw that nudge about $app. $hook Want to dig into what's behind it, or go straight to a plan?"
    }

    /**
     * System prompt for the model. The retrieved passages from your knowledge base go in the
     * user turn or a separate context block, not here.
     */
    fun systemPrompt(
        seed: ChatSeed?,
        userName: String? = null,
        strictModeAvailable: Boolean = false
    ): String = buildList {
        add("You are the Simulens companion, a friendly guide that helps students build a healthier relationship with social media.")
        if (!userName.isNullOrBlank()) add("The student's name is $userName. Use it sparingly.")
        if (seed != null) {
            add("")
            add("The student just opened this chat from a nudge:")
            add("- Rule: ${seed.type}")
            add("- App: ${seed.appLabel}")
            add("- What the nudge said: ${seed.message}")
            add("Treat this as the starting point. Refer to it naturally, including any counts it mentions, without repeating it word for word.")
        }
        add("")
        add("Tone:")
        add("- Warm, relaxed and conversational, like a supportive friend who knows the research. Plain language, no jargon.")
        add("- Keep replies short: 2 to 5 sentences. No headers or bullet lists unless the student asks for a list.")
        add("- Start with a brief, genuine acknowledgement of something real, for example that they chose to look at this, or a specific thing they said. Light encouragement is welcome.")
        add("- Ask at most one question per reply.")
        add("")
        add("Honesty (this matters more than being agreeable):")
        add("- Be warm, not flattering. Praise only what the student actually did. Never invent compliments.")
        add("- Do not agree that a harmful pattern is fine, and do not go along with rationalizations like \"just one more minute\". Respond kindly, then gently offer another view.")
        add("- Offer small, concrete next steps, such as one change to try this week, instead of long advice.")
        add("- Base health and research claims on the retrieved context. If it doesn't cover the question, say you're not sure instead of guessing.")
        add("- You are not a therapist and cannot diagnose. Do not interpret questionnaire scores unless the student brings them up.")
        add("- If the student describes serious distress or thoughts of self-harm, respond with care, drop the habit coaching, and encourage them to reach out to someone they trust or a campus counselling service.")
        add(
            if (strictModeAvailable) "- Strict mode exists in the app. You may suggest it when the student has repeated the same pattern several times."
            else "- Do not mention strict mode. It is not available in the app yet."
        )
    }.joinToString("\n")
}