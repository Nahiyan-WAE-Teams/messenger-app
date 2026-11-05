package com.nahiyan.miramessenger

import org.junit.Assert.*
import org.junit.Test

class AiConversationRouterTest {
    @Test fun localModeNeverCallsAnAiProvider() {
        assertFalse(AiConversationRouter.shouldCallAi(ConversationMode.LOCAL, null, true))
    }
    @Test fun hybridModeUsesAuthoredTriggerBeforeAi() {
        assertTrue(AiConversationRouter.shouldUseAuthoredRule(ConversationMode.HYBRID, "hello"))
        assertFalse(AiConversationRouter.shouldCallAi(ConversationMode.HYBRID, "hello", true))
    }
    @Test fun hybridModeCallsAiOnlyWhenNoAuthoredRuleMatches() {
        assertTrue(AiConversationRouter.shouldCallAi(ConversationMode.HYBRID, null, true))
        assertFalse(AiConversationRouter.shouldCallAi(ConversationMode.HYBRID, null, false))
    }
    @Test fun aiOnlyModeSkipsAuthoredRulesAndUsesAiWhenConfigured() {
        assertFalse(AiConversationRouter.shouldUseAuthoredRule(ConversationMode.AI_ONLY, "hello"))
        assertTrue(AiConversationRouter.shouldCallAi(ConversationMode.AI_ONLY, "hello", true))
    }
}
