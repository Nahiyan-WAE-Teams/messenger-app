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
            ProfileEntity("c", "C", 25, "", interests="football, travel", values="adventure")
        )
        val ranked = DiscoveryRanker.rank(profiles, interests=setOf("books", "art"))
        assertTrue(ranked.first().id in listOf("a", "b"))
        assertEquals("c", ranked[1].id)
    }
}
