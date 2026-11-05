package com.nahiyan.miramessenger

data class DialogueRuleEditorEntry(val trigger: String, val reply: String)

object DialogueRuleCodec {
    fun parse(raw: String): List<DialogueRuleEditorEntry> = raw.lines().mapNotNull { line ->
        val split = line.split("|", limit = 2)
        if (split.size != 2 || split[0].isBlank() || split[1].isBlank()) null
        else DialogueRuleEditorEntry(split[0].trim(), split[1].trim())
    }

    fun serialize(entries: List<DialogueRuleEditorEntry>): String = entries
        .map { it.trigger.trim() to it.reply.trim() }
        .filter { it.first.isNotBlank() && it.second.isNotBlank() }
        .joinToString("\n") { "${it.first} | ${it.second}" }
}
