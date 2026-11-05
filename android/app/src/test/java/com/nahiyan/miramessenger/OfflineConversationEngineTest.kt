package com.nahiyan.miramessenger

import org.junit.Assert.*
import org.junit.Test

class OfflineConversationEngineTest {
    private val profile = ProfileEntity(
        id = "test-character",
        name = "Mara",
        age = 27,
        bio = "A thoughtful designer.",
        interests = "Photography, Cooking",
        personalityTags = "Reflective, Thoughtful",
        dialogueRules = "architecture | Tell me about the building you found."
    )

    @Test fun explicitCharacterDialogueRulesTakePriorityOverEmotionalFallback() {
        val result = OfflineConversationEngine.reply(
            profile, "I'm stressed about architecture", "first-meeting", emptyList(), "", 0
        )
        assertEquals("Tell me about the building you found.", result)
    }

    @Test fun stressReceivesSupportRatherThanGenericFallback() {
        val result = OfflineConversationEngine.reply(
            profile.copy(dialogueRules = ""), "I've had a really rough day and feel overwhelmed",
            "first-meeting", emptyList(), "", 0
        )
        assertTrue(result.contains("rough") || result.contains("hardest") || result.contains("heavy"))
        assertTrue(result.contains("talk") || result.contains("listen") || result.contains("hardest"))
    }

    @Test fun establishedRelationshipUsesWarmerSupportWithoutShowingAProgressScore() {
        val first = OfflineConversationEngine.reply(
            profile.copy(dialogueRules = ""), "I'm feeling lonely", "first-meeting", emptyList(), "", 0
        )
        val familiar = OfflineConversationEngine.reply(
            profile.copy(dialogueRules = ""), "I'm feeling lonely", "familiar", emptyList(), "", 8
        )
        assertNotEquals(first, familiar)
        assertFalse(familiar.contains("%"))
    }

    @Test fun positiveNewsGetsAnInterestedCelebratoryReaction() {
        val result = OfflineConversationEngine.reply(
            profile.copy(dialogueRules = ""), "I have some great news!", "getting-to-know", emptyList(), "", 2
        )
        assertTrue(result.contains("news") || result.contains("story") || result.contains("excited") || result.contains("day"))
    }

    @Test fun askingAboutCharacterReturnsAnInterestFromHerProfile() {
        val result = OfflineConversationEngine.reply(
            profile.copy(dialogueRules = ""), "And you? What do you like?", "familiar", emptyList(), "", 6
        )
        assertTrue(result.lowercase().contains("photography"))
    }

    @Test fun characterDoesNotRepeatTheMostRecentContextualReply() {
        val first = OfflineConversationEngine.reply(
            profile.copy(dialogueRules = ""), "I'm stressed", "first-meeting", emptyList(), "", 0
        )
        val next = OfflineConversationEngine.reply(
            profile.copy(dialogueRules = ""), "I'm stressed", "first-meeting", listOf(first), "", 0
        )
        assertNotEquals(first, next)
    }

    @Test fun genericFallbackStillReflectsCharacterPersonality() {
        val result = OfflineConversationEngine.reply(
            profile.copy(dialogueRules = ""), "I saw something interesting today",
            "first-meeting", emptyList(), "", 0
        )
        assertTrue(result.contains("turning") || result.contains("story"))
    }
}
