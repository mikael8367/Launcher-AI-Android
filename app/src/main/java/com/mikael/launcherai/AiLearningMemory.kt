package com.mikael.launcherai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small, local and transparent adaptation memory. It stores only AI requests,
 * proposed commands, and the user's explicit success/failure feedback.
 */
class AiLearningMemory(context: Context) {
    companion object {
        private const val PREFS = "launcher_ai_learning"
        private const val KEY_EVENTS = "events"
        private const val MAX_EVENTS = 30
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun record(request: String, command: String, outcome: String) {
        val events = readEvents()
        events.put(JSONObject()
            .put("request", request.take(240))
            .put("command", command.take(160))
            .put("outcome", outcome)
            .put("time", System.currentTimeMillis()))
        while (events.length() > MAX_EVENTS) events.remove(0)
        prefs.edit().putString(KEY_EVENTS, events.toString()).apply()
    }

    @Synchronized
    fun summary(): String {
        val events = readEvents()
        if (events.length() == 0) return "Ainda não há exemplos de feedback do usuário."
        val lines = mutableListOf<String>()
        for (i in 0 until events.length()) {
            val e = events.optJSONObject(i) ?: continue
            lines.add("- Pedido: ${e.optString("request")} | Comando: ${e.optString("command")} | Feedback: ${e.optString("outcome")}")
        }
        return lines.takeLast(12).joinToString("\n")
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_EVENTS).apply()
    }

    private fun readEvents(): JSONArray = try {
        JSONArray(prefs.getString(KEY_EVENTS, "[]"))
    } catch (_: Exception) {
        JSONArray()
    }
}
