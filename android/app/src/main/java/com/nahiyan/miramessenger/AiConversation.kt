package com.nahiyan.miramessenger

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AiProvider { GEMINI, OPENROUTER }
enum class ConversationMode { LOCAL, HYBRID, AI_ONLY }

data class AiPreferences(
    val provider: AiProvider = AiProvider.GEMINI,
    val model: String = "gemini-2.5-flash",
    val mode: ConversationMode = ConversationMode.LOCAL,
    val hasKey: Boolean = false
)

data class AiRuntimeConfig(val preferences: AiPreferences, val apiKey: String)

class AiConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("lovely_ai_settings", Context.MODE_PRIVATE)
    private val alias = "lovely_ai_key_v1"
    fun loadPreferences(): AiPreferences = AiPreferences(
        provider = runCatching { AiProvider.valueOf(prefs.getString("provider", "GEMINI")!!) }.getOrDefault(AiProvider.GEMINI),
        model = prefs.getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash",
        mode = runCatching { ConversationMode.valueOf(prefs.getString("mode", "LOCAL")!!) }.getOrDefault(ConversationMode.LOCAL),
        hasKey = prefs.contains("key_cipher")
    )
    fun loadRuntime(): AiRuntimeConfig {
        val iv = prefs.getString("key_iv", null)
        val cipherText = prefs.getString("key_cipher", null)
        val key = if (iv == null || cipherText == null) "" else runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrDefault("")
        return AiRuntimeConfig(loadPreferences(), key)
    }
    fun save(provider: AiProvider, model: String, mode: ConversationMode, newKey: String?, clearKey: Boolean = false) {
        val editor = prefs.edit().putString("provider", provider.name)
            .putString("model", model.trim().ifBlank { if (provider == AiProvider.GEMINI) "gemini-2.5-flash" else "openrouter/auto" })
            .putString("mode", mode.name)
        if (clearKey) {
            editor.remove("key_iv").remove("key_cipher")
        } else if (!newKey.isNullOrBlank()) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            editor.putString("key_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            editor.putString("key_cipher", Base64.encodeToString(cipher.doFinal(newKey.trim().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
        }
        editor.apply()
    }
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
}

object AiConversationRouter {
    fun shouldUseAuthoredRule(mode: ConversationMode, matchedRule: String?): Boolean =
        matchedRule != null && mode != ConversationMode.AI_ONLY
    fun shouldCallAi(mode: ConversationMode, matchedRule: String?, hasKey: Boolean): Boolean =
        hasKey && when (mode) {
            ConversationMode.LOCAL -> false
            ConversationMode.HYBRID -> matchedRule == null
            ConversationMode.AI_ONLY -> true
        }
}

object AiChatClient {
    fun generate(config: AiRuntimeConfig, profile: ProfileEntity, history: List<ChatMessageEntity>): String {
        require(config.apiKey.isNotBlank()) { "Add an API key in Profile → AI conversations." }
        require(profile.kind == "fictional") { "AI replies are only available for fictional characters." }
        val system = "You are ${profile.name}, a fictional adult woman. Stay consistent with this character. Bio: ${profile.bio}. Interests: ${profile.interests}. Values: ${profile.values}. Personality: ${profile.personalityTags}. Lifestyle: ${profile.lifestyle}. Speak naturally, with a distinct voice. Respond to the actual latest message. Do not invent shared memories or facts about the user. Do not pressure the user to disclose feelings. Keep most replies concise and conversational."
        val recent = history.takeLast(16)
        return if (config.preferences.provider == AiProvider.GEMINI) {
            val contents = JSONArray()
            recent.forEach { message ->
                contents.put(JSONObject().put("role", if (message.sender == "me") "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", message.message))))
            }
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("contents", contents)
                .put("generationConfig", JSONObject().put("temperature", 0.85).put("maxOutputTokens", 350))
            val url = "https://generativelanguage.googleapis.com/v1beta/models/${config.preferences.model}:generateContent?key=${config.apiKey}"
            val response = request(url, "{}", body.toString(), null)
            response.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
                ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty().trim()
                .ifBlank { throw IllegalStateException("The AI provider returned an empty reply.") }
        } else {
            val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
            recent.forEach { messages.put(JSONObject().put("role", if (it.sender == "me") "user" else "assistant").put("content", it.message)) }
            val body = JSONObject().put("model", config.preferences.model).put("messages", messages)
                .put("temperature", 0.85).put("max_tokens", 350)
            val response = request("https://openrouter.ai/api/v1/chat/completions", config.apiKey, body.toString(), "Bearer")
            response.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty().trim()
                .ifBlank { throw IllegalStateException("The AI provider returned an empty reply.") }
        }
    }
    private fun request(url: String, key: String, body: String, authPrefix: String?): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            if (authPrefix == null) connection.setRequestProperty("x-goog-api-key", key)
            else connection.setRequestProperty("Authorization", "$authPrefix $key")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val payload = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("AI request failed (HTTP $code). Check provider, model, key and quota.")
            return JSONObject(payload)
        } finally { connection.disconnect() }
    }
}
