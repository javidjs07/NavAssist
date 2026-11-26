package com.example.navassist

import android.content.Context

enum class Provider(val label: String, val defaultModel: String, val keyUrl: String) {
    OPENAI("OpenAI (ChatGPT)", "gpt-4o-mini", "platform.openai.com/api-keys"),
    ANTHROPIC("Anthropic (Claude)", "claude-haiku-5-5", "console.anthropic.com"),
    GEMINI("Google Gemini", "gemini-2.5-flash", "aistudio.google.com/apikey"),
    CUSTOM("Custom (OpenAI-compatible)", "", "OpenRouter, Groq, or your own server")
}

data class AiConfig(val provider: Provider, val model: String, val baseUrl: String, val key: String) {
    val ready: Boolean
        get() = key.isNotBlank() && model.isNotBlank() &&
            (provider != Provider.CUSTOM || baseUrl.startsWith("https://"))

    companion object {
        fun load(c: Context): AiConfig {
            val p = try {
                Provider.valueOf(Prefs.aiProvider(c))
            } catch (e: Exception) {
                Provider.OPENAI
            }
            val model = Prefs.aiModel(c).ifBlank { p.defaultModel }
            return AiConfig(p, model, Prefs.aiBase(c), Secrets.decrypt(Prefs.aiKeyEnc(c)))
        }

        fun save(c: Context, p: Provider, model: String, base: String, key: String) {
            Prefs.saveAi(c, p.name, model.trim(), base.trim().trimEnd('/'), Secrets.encrypt(key.trim()))
        }
    }
}
