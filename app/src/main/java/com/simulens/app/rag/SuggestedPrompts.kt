package com.simulens.app.rag

/**
 * One entry per kind of intervention. Map YOUR intervention types onto these (or add / rename entries).
 *
 * prompts      = what the user sees as tappable suggestions. Keep them short and answerable from your
 *                references (research on social media and wellbeing, plus practical strategies).
 *                Avoid prompts that need personal data, such as "how much did I use today?".
 * contextLine  = one neutral sentence sent to the assistant so it knows why the chat was opened.
 *                It contains no personal data, and the server treats it as reference data only.
 */
enum class InterventionTopic(
    val headline: String,
    val contextLine: String,
    val prompts: List<String>
) {
    GENERAL(
        headline = "Questions about social media and you",
        contextLine = "",
        prompts = listOf(
            "How does social media affect my sleep?",
            "Why is it so hard to stop scrolling?",
            "Does social media hurt my concentration when studying?",
            "What are simple ways to cut down without deleting my apps?"
        )
    ),
    COMPULSIVE_LAUNCHES(
        headline = "Opening apps out of habit",
        contextLine = "A notice was just shown about repeatedly opening social media apps in a short time.",
        prompts = listOf(
            "Why do I keep opening apps without thinking?",
            "What is a habit loop and how can I break it?",
            "What can I do in the moment I feel the urge to check my phone?",
            "Does checking my phone often make me more anxious?"
        )
    ),
    DISTRACTION_SPIKE(
        headline = "Switching between apps quickly",
        contextLine = "A notice was just shown about rapid switching between apps.",
        prompts = listOf(
            "Why do I switch between apps so quickly?",
            "How does constant switching affect my focus?",
            "How long does it take to refocus after checking my phone?",
            "What helps me stay on one task for longer?"
        )
    ),
    LECTURE_HOURS(
        headline = "Phone use during class time",
        contextLine = "A notice was just shown about social media use during scheduled class hours.",
        prompts = listOf(
            "How does phone use in class affect learning?",
            "Why do I feel the need to check my phone during lectures?",
            "What can I do to stay focused in class?",
            "Does social media use affect academic performance?"
        )
    ),
    LATE_NIGHT(
        headline = "Late-night phone use",
        contextLine = "A notice was just shown about social media use late at night.",
        prompts = listOf(
            "How does using my phone at night affect my sleep?",
            "Why is it so hard to put my phone down at bedtime?",
            "What is a good wind-down routine without my phone?",
            "Does the light from my screen really matter for sleep?"
        )
    ),
    LONG_SESSION(
        headline = "Long scrolling sessions",
        contextLine = "A notice was just shown about a long continuous social media session.",
        prompts = listOf(
            "Why do I lose track of time on social media?",
            "What makes endless feeds so hard to leave?",
            "How can I take healthy breaks while using social media?",
            "How do I know if my social media use is becoming a problem?"
        )
    );

    companion object {
        /** Safe lookup for a value that came from a navigation argument or an intent extra. */
        fun fromKey(key: String?): InterventionTopic =
            values().firstOrNull { it.name == key } ?: GENERAL
    }
}