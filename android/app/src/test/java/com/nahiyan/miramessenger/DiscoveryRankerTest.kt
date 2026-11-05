package com.nahiyan.miramessenger

import org.junit.Assert.*
import org.junit.Test

class DiscoveryRankerTest {
    @Test fun ageAndExcludedHistoryFilterCandidates() {
        val profiles = listOf(ProfileEntity("a", "Ava", 25, "Bio"), ProfileEntity("b", "Maya", 32, "Bio"))
        assertEquals(listOf("a"), DiscoveryRanker.rank(profiles, minAge=24, maxAge=28, excluded=setOf("b")).map { it.id })
    }
    @Test fun sharedInterestsInfluenceRankingWithoutExposingAScore() {
        val profiles = listOf(ProfileEntity("b", "Maya", 25, "Bio", interests="sports, travel"), ProfileEntity("a", "Ava", 25, "Bio", interests="books, art"))
        assertEquals("a", DiscoveryRanker.rank(profiles, interests=setOf("books", "art")).first().id)
    }
    @Test fun greedyDiversityPreventsSimilarProfilesFromClustering() {
        val profiles = listOf(
            ProfileEntity("a", "A", 25, "", interests="books, art", values="kindness"),
            ProfileEntity("b", "B", 25, "", interests="books, art", values="kindness"),
            ProfileEntity("c", "C", 25, "", interests="football, travel", values="adventure", personalityTags="outgoing")
        )
        val ranked = DiscoveryRanker.rank(profiles, interests=setOf("books", "art"))
        assertTrue(ranked.first().id in listOf("a", "b"))
        assertEquals("c", ranked[1].id)
    }
    @Test fun recommendationReasonsOnlyUseActualSharedSignals() {
        val profile = ProfileEntity("a", "Ava", 25, "Bio", interests="books, travel", values="honesty", lifestyle="outdoors")
        val reasons = DiscoveryRanker.explain(
            profile,
            interests=setOf("books"),
            values=setOf("honesty"),
            lifestyle=setOf("homebody"),
            personality=setOf("playful")
        )
        assertEquals(listOf("Shared interests", "Similar values"), reasons)
    }

    @Test fun bundledCharactersHaveDistinctOfflineVoices() {
        val ava = StarterDialogue.apply(ProfileEntity("ava-morgan", "Ava Morgan", 25, "Bio"))
        val nina = StarterDialogue.apply(ProfileEntity("nina-park", "Nina Park", 25, "Bio"))
        assertNotEquals(ava.dialogueRules, nina.dialogueRules)
        assertNotEquals(ava.fallback, nina.fallback)
        assertTrue(DialogueEngine.reply("architecture", ava.dialogueRules, ava.fallback, "first-meeting", emptyList(), "").contains("buildings"))
        assertTrue(DialogueEngine.reply("reading", nina.dialogueRules, nina.fallback, "first-meeting", emptyList(), "").contains("reading"))
    }

    @Test fun replySelectionAvoidsRepeatingRecentCharacterReply() {
        assertEquals("Second reply", DialogueEngine.reply("hello", "hello | First reply;Second reply", "fallback", "first-meeting", listOf("First reply"), ""))
    }

    @Test fun unmatchedMessageUsesCharacterSpecificFallback() {
        val fallback = "I was making tea. What happened?"
        assertEquals(fallback, DialogueEngine.reply("xyzzy", "hello | Hi", fallback, "first-meeting", emptyList(), ""))
    }
}
