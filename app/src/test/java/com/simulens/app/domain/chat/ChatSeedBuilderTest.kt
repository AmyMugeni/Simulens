package com.simulens.app.domain.chat

import com.simulens.app.domain.rules.intervention.InterventionType
import com.simulens.app.rag.ChatLaunchViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSeedBuilderTest {

    private fun seed(type: InterventionType?, cls: String? = null, n: Int? = null, id: String = "1") =
        ChatSeed(id = id, type = type, appName = "TikTok", packageName = "com.tt", className = cls, openCount = n)

    @Test
    fun classOpenerUsesAppClassAndCount() {
        val o = ChatSeedBuilder.opener(seed(InterventionType.SCHEDULE_CONFLICT, "Calculus", 3))
        assertEquals(
            "I noticed TikTok opened 3 times in Calculus today. No judgment. " +
                    "What was going on right before you reached for it?",
            o.text
        )
        assertEquals(
            listOf("Felt bored", "Waiting on a message", "Deadline stress", "Why does this affect my studies?"),
            o.chips.map { it.label }
        )
    }

    @Test
    fun classOpenerFallsBackWithoutDetails() {
        val o = ChatSeedBuilder.opener(seed(InterventionType.SCHEDULE_CONFLICT))
        assertTrue(o.text.startsWith("I noticed TikTok opened during class today."))
    }

    @Test
    fun sleepSessionAndMorningOpeners() {
        assertEquals(
            "It's late and you're still on TikTok. What's keeping you up tonight?",
            ChatSeedBuilder.opener(seed(InterventionType.WIND_DOWN)).text
        )
        assertEquals(
            "That's a long stretch on TikTok. What were you looking for when you started?",
            ChatSeedBuilder.opener(seed(InterventionType.HIGH_DAILY_DEVIATION)).text
        )
        val morning = ChatSeedBuilder.opener(seed(InterventionType.MORNING))
        assertEquals("You opened TikTok soon after waking. What were you hoping to find?", morning.text)
        assertEquals(
            listOf("Habit", "Checking messages", "Bored", "Why does a morning scroll matter?"),
            morning.chips.map { it.label }
        )
    }

    @Test
    fun chipQueriesNeverLeakAppOrClass() {
        InterventionType.values().forEach { type ->
            val o = ChatSeedBuilder.opener(seed(type, "Calculus", 3))
            o.chips.forEach {
                assertFalse(it.query.contains("TikTok"))
                assertFalse(it.query.contains("Calculus"))
            }
        }
    }

    @Test
    fun sameNudgeIsOfferedOnce() {
        val vm = ChatLaunchViewModel()
        assertTrue(vm.offer(seed(InterventionType.MORNING, id = "a")))
        vm.consume("a")
        assertFalse(vm.offer(seed(InterventionType.MORNING, id = "a")))
        assertNull(vm.pending.value)
        assertTrue(vm.offer(seed(InterventionType.MORNING, id = "b")))
    }
}
