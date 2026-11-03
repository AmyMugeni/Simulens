package com.simulens.app.domain.rules.intervention

import java.time.Instant
import java.time.ZoneId

/** One past session reduced to the flags the nudge copy cares about. */
data class SessionFlags(
    val endEpochMs: Long,
    val inClass: Boolean,
    val bedtime: Boolean,
    val morning: Boolean
)

/**
 * How often the user already did this with one app, BEFORE the current visit.
 * The current session is not in the database until it closes, so the copy adds 1.
 */
data class RepeatFacts(
    val classToday: Int = 0,
    val classWeek: Int = 0,
    val bedtimeToday: Int = 0,
    val bedtimeWeek: Int = 0,
    val morningWeek: Int = 0
)

fun buildRepeatFacts(flags: List<SessionFlags>, nowMs: Long, zone: ZoneId): RepeatFacts {
    val todayStart = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        .atStartOfDay(zone).toInstant().toEpochMilli()
    val weekStart = nowMs - 7L * 24 * 60 * 60 * 1000
    val week = flags.filter { it.endEpochMs >= weekStart }
    val today = week.filter { it.endEpochMs >= todayStart }
    return RepeatFacts(
        classToday = today.count { it.inClass },
        classWeek = week.count { it.inClass },
        bedtimeToday = today.count { it.bedtime },
        bedtimeWeek = week.count { it.bedtime },
        morningWeek = week.count { it.morning }
    )
}

object NudgeCopy {

    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
            1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th"
        }
        return "$n$suffix"
    }

    private fun times(n: Int) = if (n == 2) "twice" else "$n times"

    /** Rotates phrasing so repeated nudges don't read identically. */
    private fun pick(n: Int, vararg options: String) = options[n % options.size]

    fun classMessage(
        subject: String,
        until: String,
        app: String,
        f: RepeatFacts,
        strictModeAvailable: Boolean
    ): String {
        val today = f.classToday + 1
        val week = f.classWeek + 1
        return when {
            week >= 8 && strictModeAvailable ->
                "That's the ${ordinal(week)} time this week you've opened $app during class. " +
                        "Strict mode could take the decision off your plate during class hours. Want to talk it through?"
            week >= 8 ->
                "That's the ${ordinal(week)} time this week you've opened $app during class. " +
                        "Something keeps pulling you in. Want to work out a better plan together?"
            today >= 2 -> pick(
                today,
                "That's the ${ordinal(today)} time today you've reached for $app during $subject. What's pulling you in? Happy to talk it through.",
                "You've opened $app ${times(today)} during class today. Want to figure out what makes it hard to wait?"
            )
            else -> "$subject runs until $until. $app will still be there afterwards."
        }
    }

    fun sleepMessage(wake: String, app: String, f: RepeatFacts): String {
        val week = f.bedtimeWeek + 1
        return if (week >= 4)
            "That's the ${ordinal(week)} bedtime visit to $app this week. Want to look at what keeps you up?"
        else
            "You planned to be asleep until $wake. Put $app down and rest."
    }

    fun windDownMessage(sleepTime: String, app: String, f: RepeatFacts): String {
        val week = f.bedtimeWeek + 1
        return if (week >= 4)
            "Wind-down time, and $app is open again. That's the ${ordinal(week)} bedtime visit this week. " +
                    "Want to find a routine that works better?"
        else
            "Your sleep window starts at $sleepTime. A feed now makes it harder to switch off."
    }

    fun morningMessage(wake: String, app: String, f: RepeatFacts): String {
        val week = f.morningWeek + 1
        return if (week >= 4)
            "That's the ${ordinal(week)} time this week you've opened $app right after waking. " +
                    "Want to try a different first half hour?"
        else
            "You woke at $wake. The first half hour is easier without $app."
    }
}