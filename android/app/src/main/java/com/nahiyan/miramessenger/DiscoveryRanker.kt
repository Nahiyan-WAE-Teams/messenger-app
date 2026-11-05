package com.nahiyan.miramessenger

object DiscoveryRanker {
    private fun tokens(value: String) = value.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    private fun overlap(preferred: Set<String>, actual: String): Double {
        val right = tokens(actual)
        if (preferred.isEmpty() || right.isEmpty()) return 0.0
        return preferred.map { it.lowercase() }.toSet().intersect(right).size.toDouble() / maxOf(preferred.size, right.size)
    }
    private fun similarity(a: ProfileEntity, b: ProfileEntity): Double =
        overlap(tokens(a.interests), b.interests) * .55 +
        overlap(tokens(a.personalityTags), b.personalityTags) * .25 +
        overlap(tokens(a.values), b.values) * .20

    fun rank(
        profiles: List<ProfileEntity>, interests: Set<String> = emptySet(), values: Set<String> = emptySet(),
        lifestyle: Set<String> = emptySet(), personality: Set<String> = emptySet(), intent: String = "Any",
        minAge: Int = 18, maxAge: Int = 99, excluded: Set<String> = emptySet(), seed: Int = 0
    ): List<ProfileEntity> {
        fun fit(p: ProfileEntity): Double {
            val signals = listOf(
                Triple(overlap(interests, p.interests), .28, interests.isNotEmpty()),
                Triple(overlap(values, p.values), .22, values.isNotEmpty()),
                Triple(overlap(lifestyle, p.lifestyle), .16, lifestyle.isNotEmpty()),
                Triple(overlap(personality, p.personalityTags), .12, personality.isNotEmpty()),
                Triple(if (intent == "Any" || intent.isBlank() || p.intent == intent) 1.0 else 0.0, .14, intent != "Any" && intent.isNotBlank()),
                Triple(1.0, .08, true)
            ).filter { it.third }
            val weight = signals.sumOf { it.second }
            return if (weight == 0.0) .5 else signals.sumOf { it.first * it.second } / weight
        }
        val candidates = profiles.filter { it.active && it.id !in excluded && it.age in minAge..maxAge && (intent == "Any" || it.intent == intent) }
        val remaining = candidates.toMutableList()
        val ranked = mutableListOf<ProfileEntity>()
        while (remaining.isNotEmpty()) {
            val chosen = remaining.maxWithOrNull(compareBy<ProfileEntity> { p ->
                val diversity = if (ranked.isEmpty()) 1.0 else 1.0 - ranked.maxOf { similarity(p, it) }
                fit(p) * .47 + diversity * .48 + ((p.id.hashCode() xor seed).toUInt().toLong() / 4294967295.0) * .05
            }.thenBy { it.id })!!
            ranked += chosen
            remaining.remove(chosen)
        }
        return ranked
    }
}
