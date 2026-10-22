package com.cognilens.app.data.chat

import com.cognilens.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Source(val n: Int, val title: String, val url: String)
enum class Mode { ANSWER, NO_INFO, CRISIS }
data class Reply(val answer: String, val sources: List<Source>, val mode: Mode)
data class Turn(val role: String, val content: String) // role: "user" or "assistant"

class ChatClient(
    // Free Render instances sleep when idle; the first request can take up to a minute.
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(75, TimeUnit.SECONDS)
        .build()
) {
    suspend fun ask(message: String, history: List<Turn>, context: String): Reply =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("message", message)
                .put("context", context)
                .put("history", JSONArray().also { arr ->
                    history.takeLast(6).forEach {
                        arr.put(JSONObject().put("role", it.role).put("content", it.content.take(1000)))
                    }
                })
                .toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${BuildConfig.RAG_BASE_URL.trimEnd('/')}/chat")
                .header("X-App-Token", BuildConfig.RAG_APP_TOKEN)
                .post(body)
                .build()

            http.newCall(request).execute().use { r ->
                if (!r.isSuccessful) {
                    throw IOException(
                        when (r.code) {
                            401 -> "The assistant could not verify this app."
                            429 -> "You're sending messages quickly. Please wait a moment."
                            502, 503 -> "The assistant is busy right now. Please try again shortly."
                            else -> "Something went wrong (code ${r.code})."
                        }
                    )
                }
                val json = JSONObject(r.body?.string().orEmpty())
                val sources = json.optJSONArray("sources") ?: JSONArray()
                Reply(
                    answer = json.getString("answer"),
                    sources = (0 until sources.length()).map {
                        val s = sources.getJSONObject(it)
                        Source(s.getInt("n"), s.getString("title"), s.getString("url"))
                    },
                    mode = when (json.optString("mode")) {
                        "crisis" -> Mode.CRISIS
                        "no_info" -> Mode.NO_INFO
                        else -> Mode.ANSWER
                    }
                )
            }
        }
}
