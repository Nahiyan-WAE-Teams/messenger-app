package com.nahiyan.miramessenger

object DiscoveryRanker {
    fun rank(
        profiles: List<ProfileEntity>,
        interests: Set<String> = emptySet(),
        values: Set<String> = emptySet(),
        lifestyle: Set<String> = emptySet(),
        personality: Set<String> = emptySet(),
        minAge: Int = 18,
        maxAge: Int = 99
    ): List<ProfileEntity> {
        fun overlap(preferred: Set<String>, actual: String): Double {
            if (preferred.isEmpty()) return 0.0
            val tokens = actual.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            if (tokens.isEmpty()) return 0.0
            return preferred.map { it.lowercase() }.toSet().intersect(tokens).size.toDouble() / maxOf(preferred.size, tokens.size)
        }
        return profiles.asSequence().filter { it.active && it.age in minAge..maxAge }
            .map { p ->
                val signals = listOf(
                    Triple(overlap(interests, p.interests), .28, interests.isNotEmpty()),
                    Triple(overlap(values, p.values), .22, values.isNotEmpty()),
                    Triple(overlap(lifestyle, p.lifestyle), .16, lifestyle.isNotEmpty()),
                    Triple(overlap(personality, p.personalityTags), .12, personality.isNotEmpty()),
                    Triple(1.0, .08, true)
                ).filter { it.third }
                val total = signals.sumOf { it.second }
                p to if (total == 0.0) .5 else signals.sumOf { it.first * it.second } / total
            }.sortedWith(compareByDescending<Pair<ProfileEntity, Double>> { it.second }.thenBy { it.first.id })
            .map { it.first }.toList()
    }
}
