package com.fairshare.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The server answered with an error. [status] is the HTTP status (401 = log in again). */
class ApiException(val status: Int, message: String) : Exception(message)

/**
 * Client of server/api/api.php: POST <baseUrl>?action=<name> with a JSON body, JSON back.
 * Network problems are thrown as [IOException] (the phone is offline: try again later),
 * answers with "ok": false as [ApiException].
 */
class FairShareApi(private val baseUrl: String, private val token: () -> String?) {

    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    suspend fun call(
        action: String,
        body: JSONObject = JSONObject(),
        readTimeoutMs: Int = 30_000,
    ): JSONObject = withContext(Dispatchers.IO) {
        if (!isConfigured) throw ApiException(0, "Brak adresu serwera: ustaw fairshare.apiUrl w gradle.properties")
        val separator = if ('?' in baseUrl) '&' else '?'
        val connection = URL("$baseUrl${separator}action=$action").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            token()?.let { connection.setRequestProperty("X-FairShare-Token", it) }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status < 400) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            val json = try {
                JSONObject(text)
            } catch (e: JSONException) {
                // e.g. an HTML error page, or PHP not running and the source coming back as text
                throw ApiException(status, "Serwer odpowiedział nieoczekiwanie (HTTP $status)")
            }
            if (status >= 400 || !json.optBoolean("ok")) {
                throw ApiException(status, json.optString("error").ifBlank { "Błąd serwera (HTTP $status)" })
            }
            json
        } finally {
            connection.disconnect()
        }
    }
}
