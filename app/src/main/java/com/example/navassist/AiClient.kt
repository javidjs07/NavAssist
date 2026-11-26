package com.example.navassist

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

data class ChatMsg(val role: String, val text: String) // role: "user" or "assistant"

/** Minimal cloud chat client (no libraries). Call from a background thread. */
object AiClient {
    data class Result(val ok: Boolean, val text: String)

    private const val SYSTEM =
        "You are NavAssist AI, a friendly assistant on an Android phone. Answer clearly and briefly " +
            "(under 120 words unless asked for more). Reply in the same language the user speaks. " +
            "Your answers may be read aloud, so avoid markdown tables and long lists."

    fun chat(cfg: AiConfig, msgs: List<ChatMsg>): Result {
        return try {
            when (cfg.provider) {
                Provider.OPENAI -> openAi("https://api.openai.com/v1/chat/completions", cfg, msgs)
                Provider.CUSTOM -> openAi(cfg.baseUrl + "/chat/completions", cfg, msgs)
                Provider.ANTHROPIC -> anthropic(cfg, msgs)
                Provider.GEMINI -> gemini(cfg, msgs)
            }
        } catch (e: UnknownHostException) {
            Result(false, "No internet connection.")
        } catch (e: SocketTimeoutException) {
            Result(false, "The AI took too long to answer. Try again.")
        } catch (e: IOException) {
            Result(false, "Network error. Check your connection.")
        } catch (e: Exception) {
            Result(false, "Something went wrong: ${e.message}")
        }
    }

    private fun openAi(url: String, cfg: AiConfig, msgs: List<ChatMsg>): Result {
        val arr = JSONArray().put(JSONObject().put("role", "system").put("content", SYSTEM))
        msgs.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.text)) }
        val body = JSONObject().put("model", cfg.model).put("messages", arr).toString()
        val (code, text) = post(url, mapOf("Authorization" to "Bearer ${cfg.key}"), body)
        if (code !in 200..299) return fail(code, text)
        val out = JSONObject(text).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content", "")
        return Result(true, out.trim().ifEmpty { "(empty reply)" })
    }

    private fun anthropic(cfg: AiConfig, msgs: List<ChatMsg>): Result {
        val arr = JSONArray()
        msgs.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.text)) }
        val body = JSONObject().put("model", cfg.model).put("max_tokens", 1024)
            .put("system", SYSTEM).put("messages", arr).toString()
        val (code, text) = post(
            "https://api.anthropic.com/v1/messages",
            mapOf("x-api-key" to cfg.key, "anthropic-version" to "2023-06-01"), body
        )
        if (code !in 200..299) return fail(code, text)
        val content = JSONObject(text).getJSONArray("content")
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val b = content.getJSONObject(i)
            if (b.optString("type") == "text") sb.append(b.optString("text"))
        }
        return Result(true, sb.toString().trim().ifEmpty { "(empty reply)" })
    }

    private fun gemini(cfg: AiConfig, msgs: List<ChatMsg>): Result {
        val contents = JSONArray()
        msgs.forEach {
            contents.put(
                JSONObject().put("role", if (it.role == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", it.text)))
            )
        }
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
            .put("contents", contents).toString()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${cfg.model}:generateContent"
        val (code, text) = post(url, mapOf("x-goog-api-key" to cfg.key), body)
        if (code !in 200..299) return fail(code, text)
        val parts = JSONObject(text).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        return Result(true, sb.toString().trim().ifEmpty { "(empty reply)" })
    }

    private fun post(url: String, headers: Map<String, String>, body: String): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = 60000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return code to text
        } finally {
            c.disconnect()
        }
    }

    private fun fail(code: Int, text: String): Result {
        val detail = try {
            JSONObject(text).optJSONObject("error")?.optString("message") ?: ""
        } catch (e: Exception) {
            ""
        }
        val msg = when (code) {
            400 -> "The request was rejected (check the model name). $detail"
            401, 403 -> "The API key was rejected. Check it in NavAssist → AI assistant."
            404 -> "Model or endpoint not found. Check the model name. $detail"
            429 -> "Rate limit or quota reached for this API key."
            else -> "Error $code. $detail"
        }
        return Result(false, msg.trim())
    }
}
