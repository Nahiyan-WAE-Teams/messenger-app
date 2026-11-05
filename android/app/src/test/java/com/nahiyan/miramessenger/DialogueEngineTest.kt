package com.nahiyan.miramessenger

import org.junit.Assert.*
import org.junit.Test

class DialogueEngineTest {
    @Test fun picksTheMostSpecificTrigger() {
        val rules = "hi | Hello; Hey\ncoffee, coffee shop | You found my weakness."
        assertEquals("You found my weakness.", DialogueEngine.reply("I love coffee shops", rules, "fallback", "first-meeting", emptyList(), ""))
    }
    @Test fun respectsStageSpecificRules() {
        val rules = "[familiar] hello | Good to see you again.\nhello | Hey there."
        assertEquals("Hey there.", DialogueEngine.reply("hello", rules, "fallback", "first-meeting", emptyList(), ""))
        assertEquals("Good to see you again.", DialogueEngine.reply("hello", rules, "fallback", "familiar", emptyList(), ""))
    }
    @Test fun contextRuleRequiresPreviousMessage() {
        val rules = "[after:work, exam] fine | Was your day a lot?"
        assertEquals("fallback", DialogueEngine.reply("fine", rules, "fallback", "first-meeting", emptyList(), ""))
        assertEquals("Was your day a lot?", DialogueEngine.reply("fine", rules, "fallback", "first-meeting", emptyList(), "work was busy"))
    }
    @Test fun avoidsRepeatingRecentReplies() {
        val rules = "hello | Hey; Hi there"
        assertEquals("Hi there", DialogueEngine.reply("hello", rules, "fallback", "first-meeting", listOf("Hey"), ""))
    }
}
