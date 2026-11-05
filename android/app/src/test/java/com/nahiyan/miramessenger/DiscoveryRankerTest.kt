package com.nahiyan.miramessenger

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveryRankerTest {
    @Test fun ageFilterExcludesOutOfRangeProfiles() {
        val profiles = listOf(ProfileEntity("a", "Ava", 25, "Bio"), ProfileEntity("b", "Maya", 32, "Bio"))
        assertEquals(listOf("a"), DiscoveryRanker.rank(profiles, minAge = 24, maxAge = 28).map { it.id })
    }

    @Test fun sharedInterestsInfluenceRankingWithoutExposingAScore() {
        val profiles = listOf(ProfileEntity("b", "Maya", 25, "Bio", interests = "sports, travel"), ProfileEntity("a", "Ava", 25, "Bio", interests = "books, art"))
        assertEquals("a", DiscoveryRanker.rank(profiles, interests = setOf("books", "art")).first().id)
    }
}
