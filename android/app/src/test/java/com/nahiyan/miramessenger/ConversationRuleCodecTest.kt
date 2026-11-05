package com.nahiyan.miramessenger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRuleCodecTest {
    @Test fun parsesEachTriggerAndReplyAsAnEditableRule() {
        val parsed = DialogueRuleCodec.parse("hello, hi | Hey there!\n[close] miss you | I missed you too.")
        assertEquals(2, parsed.size)
        assertEquals("hello, hi", parsed[0].trigger)
        assertEquals("Hey there!", parsed[0].reply)
        assertEquals("[close] miss you", parsed[1].trigger)
    }

    @Test fun serializesEditedRulesInTheFormatTheOfflineEngineUnderstands() {
        val raw = DialogueRuleCodec.serialize(listOf(
            DialogueRuleEditorEntry("hi, hello", "Hey!"),
            DialogueRuleEditorEntry("how are you", "Doing well.")
        ))
        assertEquals("hi, hello | Hey!\nhow are you | Doing well.", raw)
        assertEquals("Hey!", DialogueEngine.matchingReply("hello", raw, "first-meeting", emptyList(), ""))
    }

    @Test fun ignoresIncompleteRowsInsteadOfSavingBrokenRules() {
        val raw = DialogueRuleCodec.serialize(listOf(
            DialogueRuleEditorEntry("", "No trigger"),
            DialogueRuleEditorEntry("hello", ""),
            DialogueRuleEditorEntry("thanks", "You're welcome.")
        ))
        assertEquals("thanks | You're welcome.", raw)
        assertTrue(DialogueRuleCodec.parse(raw).size == 1)
    }
}
