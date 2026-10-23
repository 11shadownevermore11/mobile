package ru.kodnovorossii.app.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : Exception(message)

/** HTTP-клиент общего API платформы. */
class Api(private val prefs: Prefs) {
    val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun raw(method: String, path: String, body: Any? = null, token: String? = prefs.token): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(ApiConfig.url(path)).header("X-Client", "app")
            if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
            val reqBody = when {
                body is String -> body.toRequestBody(json)
                body != null -> gson.toJson(body).toRequestBody(json)
                method == "POST" -> "{}".toRequestBody(json)
                else -> null
            }
            builder.method(method, reqBody)
            try {
                client.newCall(builder.build()).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val msg = runCatching {
                            val el = JsonParser.parseString(text).asJsonObject.get("detail")
                            if (el != null && el.isJsonPrimitive) el.asString else "Проверьте заполнение полей"
                        }.getOrDefault("Ошибка сервера (${resp.code})")
                        throw ApiException(resp.code, msg)
                    }
                    text
                }
            } catch (e: IOException) {
                throw ApiException(0, "Нет соединения с сервером")
            }
        }

    suspend fun <T> call(method: String, path: String, body: Any?, type: Type, token: String? = prefs.token): T {
        val text = raw(method, path, body, token)
        return gson.fromJson(text, type)
    }

    suspend inline fun <reified T> get(path: String): T =
        call("GET", path, null, object : TypeToken<T>() {}.type)

    suspend inline fun <reified T> post(path: String, body: Any? = null, token: String? = null): T =
        call("POST", path, body, object : TypeToken<T>() {}.type, token ?: tokenOrNull())

    suspend inline fun <reified T> patch(path: String, body: Any?): T =
        call("PATCH", path, body, object : TypeToken<T>() {}.type)

    suspend fun delete(path: String) {
        raw("DELETE", path)
    }

    fun tokenOrNull(): String? = prefs.token
}
