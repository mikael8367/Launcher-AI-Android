package com.mikael.launcherai

import android.content.Context
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

/**
 * OpenAI-compatible chat client. API key is encrypted with a device-bound Android Keystore key.
 * For a public release, route requests through a trusted backend instead of shipping a shared key.
 */
class AiOnlineClient(private val context: Context) {
    companion object {
        private const val PREFS = "launcher_ai_secure"
        private const val KEY_CIPHER = "api_key_cipher"
        private const val KEY_IV = "api_key_iv"
        private const val KEY_ALIAS = "launcher_ai_api_key_v1"
        private const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-4o-mini"
    }

    fun hasKey(): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .contains(KEY_CIPHER)

    fun saveKey(raw: String) {
        val key = raw.trim()
        require(key.startsWith("sk-") && key.length > 20) { "Confira a chave da API." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CIPHER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun clearKey() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_CIPHER).remove(KEY_IV).apply()
    }

    fun ask(userRequest: String): JSONObject {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(KEY_CIPHER, null) ?: error("Configure sua chave da API primeiro.")
        val iv = prefs.getString(KEY_IV, null) ?: error("Chave criptografada incompleta.")
        val decrypt = Cipher.getInstance("AES/GCM/NoPadding")
        decrypt.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        val apiKey = String(decrypt.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)

        val system = """
            Você é o planejador de ações do aplicativo Android Launcher AI. Você NÃO tem visão da tela.
            Retorne apenas JSON válido no formato {"reply":"explicação curta em português","commands":["comando"]}.
            Use somente comandos desta lista: "abrir NOME DO APP", "voltar", "início", "recentes",
            "notificações", "configurações rápidas", "tocar X Y", "deslizar X1 Y1 X2 Y2".
            Para toque/deslize, só use coordenadas se o usuário fornecer explicitamente os números.
            Nunca invente coordenadas, não alegue ter visto a tela, não tente contornar bloqueio,
            permissões, autenticação, compras ou confirmações do Android. Se não for possível,
            retorne commands vazio e explique a limitação. Prefira um comando por vez.
        """.trimIndent()
        val payload = JSONObject()
            .put("model", MODEL)
            .put("temperature", 0.1)
            .put("max_tokens", 300)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", userRequest.take(1500))))
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 25000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (code !in 200..299) {
                val detail = try { JSONObject(body).optJSONObject("error")?.optString("message") } catch (_: Exception) { null }
                error("API HTTP $code: ${detail?.take(180) ?: "verifique a chave, o saldo e o acesso à API"}.")
            }
            val content = JSONObject(body).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
            return JSONObject(content)
        } finally {
            connection.disconnect()
        }
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
