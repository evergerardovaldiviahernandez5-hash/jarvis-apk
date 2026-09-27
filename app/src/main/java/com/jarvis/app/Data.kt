package com.jarvis.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Message(val role: String, val content: String)
data class Chat(
    val id: String,
    val title: String,
    val messages: List<Message>,
    val createdAt: Long
)

data class AppSettings(
    val systemPrompt: String = "Eres Jarvis, un asistente personal eficiente, ingenioso y leal. Respondes de forma concisa y útil.",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val contextSize: Int = 4096,
    val threads: Int = Runtime.getRuntime().availableProcessors().coerceAtMost(8)
)

object ChatRepository {
    private const val FILE = "chats.json"

    fun load(ctx: Context): List<Chat> = try {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) emptyList()
        else {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val msgs = o.getJSONArray("messages")
                val list = (0 until msgs.length()).map { j ->
                    val mo = msgs.getJSONObject(j)
                    Message(mo.getString("role"), mo.getString("content"))
                }
                Chat(
                    o.getString("id"),
                    o.getString("title"),
                    list,
                    o.optLong("createdAt", System.currentTimeMillis())
                )
            }
        }
    } catch (e: Exception) { emptyList() }

    fun save(ctx: Context, chats: List<Chat>) {
        try {
            val arr = JSONArray()
            chats.take(60).forEach { c ->
                val o = JSONObject()
                o.put("id", c.id)
                o.put("title", c.title)
                o.put("createdAt", c.createdAt)
                val msgs = JSONArray()
                c.messages.forEach { m ->
                    msgs.put(JSONObject().put("role", m.role).put("content", m.content))
                }
                o.put("messages", msgs)
                arr.put(o)
            }
            File(ctx.filesDir, FILE).writeText(arr.toString())
        } catch (_: Exception) {}
    }
}

object SettingsRepository {
    private const val PREFS = "jarvis_prefs"

    fun load(ctx: Context): AppSettings {
        val p = ctx.getSharedPreferences(PREFS, 0)
        val def = AppSettings()
        return AppSettings(
            systemPrompt = p.getString("system", null) ?: def.systemPrompt,
            temperature = p.getFloat("temp", def.temperature),
            maxTokens = p.getInt("maxTokens", def.maxTokens),
            contextSize = p.getInt("ctx", def.contextSize),
            threads = p.getInt("threads", def.threads)
        )
    }

    fun save(ctx: Context, s: AppSettings) {
        ctx.getSharedPreferences(PREFS, 0).edit()
            .putString("system", s.systemPrompt)
            .putFloat("temp", s.temperature)
            .putInt("maxTokens", s.maxTokens)
            .putInt("ctx", s.contextSize)
            .putInt("threads", s.threads)
            .apply()
    }
}
