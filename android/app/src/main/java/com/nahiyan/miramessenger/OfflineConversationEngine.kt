package com.nahiyan.miramessenger

/**
 * Deterministic, fully offline conversational reactions.
 * Explicit profile-authored dialogue rules always win; contextual reactions are a fallback.
 */
object OfflineConversationEngine {
    private fun normalize(value: String): String =
        java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFKC)
            .replace(Regex("[^\\p{L}\\p{N}\\s']"), " ")
            .replace(Regex("\\s+"), " ").trim()

    private fun containsAny(text: String, phrases: List<String>): Boolean {
        val padded = " $text "
        return phrases.any { padded.contains(" ${normalize(it)} ") }
    }

    private fun choose(options: List<String>, recent: List<String>): String =
        options.firstOrNull { it !in recent.takeLast(3) } ?: options.first()

    fun reply(
        profile: ProfileEntity,
        message: String,
        stage: String,
        recentReplies: List<String>,
        previousUserMessage: String,
        exchangeCount: Int
    ): String {
        DialogueEngine.matchingReply(
            message, profile.dialogueRules, stage, recentReplies, previousUserMessage
        )?.let { return it }

        val text = normalize(message)
        val traits = normalize(profile.personalityTags)
        val interests = profile.interests.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val established = stage == "familiar" || stage == "close" || exchangeCount >= 5

        val responses = when {
            containsAny(text, listOf("bad day", "rough day", "stressed", "stress", "anxious", "anxiety", "lonely", "sad", "upset", "overwhelmed", "exhausted", "tired", "hurt")) -> {
                when {
                    "direct" in traits -> listOf(
                        "That sounds genuinely rough. Do you want to tell me what happened, or would you rather change the subject?",
                        "I'm sorry it's been a hard day. What's the part weighing on you most?",
                        "I won't try to fix it in one sentence. Tell me what happened."
                    )
                    "reflective" in traits || "thoughtful" in traits -> if (established) listOf(
                        "I wish I could make it easier on you. You don't have to carry the whole thing alone. Want to talk?",
                        "I know some days take more out of you than they should. I'm here for the unedited version.",
                        "That sounds like a lot. We can sit with it for a minute, or find a small way to make tonight better."
                    ) else listOf(
                        "I'm sorry it's sitting so heavily. You don't have to make it sound tidy for me. What part feels hardest?",
                        "That sounds like a lot to carry. Want to talk through it a little?",
                        "I wish the day had been gentler with you. I'm listening if you want to let it out."
                    )
                    "playful" in traits || "witty" in traits -> listOf(
                        "That is a rotten combination of feelings. Want to vent, or should I attempt a distraction?",
                        "I'm sorry. We can give the day a terrible review together if that helps. What happened?",
                        "Sounds like today needs to sit in the corner and think about what it did. Want to tell me?"
                    )
                    else -> listOf(
                        "I'm sorry it's been a rough one. Want to tell me what happened?",
                        "That sounds heavy. I'm here to listen if you feel like talking.",
                        "I wish I could make the day a little lighter. Do you want to talk, or would a distraction help?"
                    )
                }
            }
            containsAny(text, listOf("great news", "good news", "so excited", "excited", "amazing", "i won", "finally", "proud of", "happy today")) -> listOf(
                "Wait, that's lovely news. What happened?",
                "Okay, I want the full story. What made your day?",
                "I can hear the excitement in that. Tell me everything."
            )
            containsAny(text, listOf("thank you", "thanks", "thx")) -> listOf(
                "Of course. I'm glad I could be here for that.",
                "You're welcome. And thank you for telling me.",
                "Anytime. I appreciate you saying that."
            )
            containsAny(text, listOf("i'm sorry", "im sorry", "my bad", "i apologize", "i apologise")) -> listOf(
                "It's okay. We can pick up from here.",
                "Thank you for saying that. I don't want one awkward moment to become the whole conversation.",
                "I appreciate the apology. Let's take it from here."
            )
            containsAny(text, listOf("you are beautiful", "you're beautiful", "you are pretty", "you're pretty", "you are cute", "you're cute", "i like you", "i miss you")) -> listOf(
                "That's sweet of you to say. I'm a little awkward with compliments, but I appreciate it.",
                "Well, that made me smile. I'm not entirely sure what to do with a compliment, apparently.",
                "You're making me blush a little. Thank you."
            )
            containsAny(text, listOf("and you", "what about you", "your favorite", "what do you like", "do you like")) && interests.isNotEmpty() -> {
                val interest = interests.first()
                listOf(
                    "I've been enjoying ${interest.lowercase()} lately. It's one of those things I can happily lose time in.",
                    "For me, probably ${interest.lowercase()}. There's usually something new to notice about it.",
                    "${interest} is high on my list. What got you interested in it?"
                )
            }
            containsAny(text, listOf("shut up", "you are stupid", "you're stupid", "you're annoying", "you are annoying", "idiot")) -> listOf(
                "I'm happy to talk things through, but let's keep it respectful.",
                "That came across pretty sharply. We can disagree without taking swings at each other."
            )
            text in listOf("and then", "what happened next", "go on", "then what") && previousUserMessage.isNotBlank() -> {
                val fragment = previousUserMessage.take(72).trimEnd()
                listOf("You were telling me: “$fragment…” What happened after that?")
            }
            message.trim().endsWith("?") && containsAny(text, listOf("how are you", "how's your day", "hows your day")) -> listOf(
                "Pretty good, thanks for asking. A little curious about how your day is going, though.",
                "Not bad. It's nice to have a proper conversation. How's yours treating you?"
            )
            else -> emptyList()
        }
        if (responses.isNotEmpty()) return choose(responses, recentReplies)

        val followUp = when {
            "direct" in traits -> listOf("I want to understand what you mean. What's the part I should focus on?", "Tell me a little more. I'd rather understand than guess.")
            "reflective" in traits || "thoughtful" in traits -> listOf("I'm turning that over in my head. What made you think of it?", "There's probably a story behind that. Want to tell me?")
            "playful" in traits || "witty" in traits -> listOf("I have a follow-up, but I'm trying not to ask five questions at once. Start wherever you like.", "Okay, I'm curious now. Give me the slightly longer version?")
            else -> listOf("I want to hear a little more. What happened next?", "Tell me more about that. What's on your mind?")
        }
        val stageAware = if (established) followUp + "I want to hear the rest, if you feel like telling me." else followUp
        return choose(stageAware, recentReplies)
    }
}
