package com.fairshare.scanner

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * Reads the receipt photo with Google Gemini (generateContent), the AI step of the recommended setup: used only when
 * the rules' items do not add up to the total. Same prompt, JSON schema and photo size (2048 px) as in the evaluation.
 *
 * Both models read the same photo equally well (44/44 in the evaluation), so the wait is what matters: when the first
 * one has not answered after [hedgeAfterMs] (on 11.11 it took over 10 s twice in a row), the second one starts too, and
 * whichever answers first wins. A model that fails fast (503) hands over at once.
 *
 * The key is put into the APK at build time (BuildConfig.GEMINI_API_KEY): fine for a test build on your own phone,
 * but in the real app this call belongs on the backend, so the key never ships in the APK.
 */
class GeminiReceiptReader(
    private val apiKey: String,
    /** Tried in order: when the first one is overloaded (503), times out or hits a limit, the next one answers. */
    private val models: List<String>,
    /** 3.5 Flash-Lite answers in 2,5 s typically, 9 in 10 under 4,3 s (WNIOSKI.md): past that the next model joins. */
    private val hedgeAfterMs: Long = 4_000,
    /** One request; a slow model keeps going while the other one tries, so it may take longer than the hedge. */
    private val requestTimeoutMs: Int = 20_000,
    /** The whole AI step, all models and retries together: past this the user gets the rules' reading. */
    private val budgetMs: Long = 25_000,
) {
    /** Requests are blocking HTTP calls: the one that loses the race finishes (or times out) on its own here. */
    private val requests = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    class Result(val receipt: ScannedReceipt, val model: String, val ms: Long, val attempts: Int)

    val firstModel: String? get() = models.firstOrNull()

    suspend fun read(upright: Bitmap): Result = withContext(Dispatchers.IO) {
        val jpeg = upright.forVision()
        val started = SystemClock.elapsedRealtime()
        fun left(): Long = budgetMs - (SystemClock.elapsedRealtime() - started)
        val answer = CompletableDeferred<Result>()
        val errors = CopyOnWriteArrayList<String>()
        val nextModel = AtomicInteger(0)
        val running = AtomicInteger(0)

        fun startNextModel() {
            val index = nextModel.getAndIncrement()
            if (index >= models.size || answer.isCompleted) return
            val model = models[index]
            running.incrementAndGet()
            requests.launch {
                try {
                    // an overloaded model (503) or a rate limit (429) often answers a second later: one retry
                    for (attempt in 1..2) {
                        val timeout = minOf(requestTimeoutMs.toLong(), left()).toInt()
                        if (timeout < 2_000 || answer.isCompleted) break
                        try {
                            val (receipt, requestCount) = readWith(model, jpeg, timeout)
                            answer.complete(Result(receipt, model, SystemClock.elapsedRealtime() - started, requestCount))
                            break
                        } catch (e: ModelUnavailable) {
                            errors += e.message.orEmpty()
                            Log.w(TAG, "Gemini, próba ${errors.size} po ${SystemClock.elapsedRealtime() - started} ms: ${e.message}")
                            if (!e.retryable || left() < 4_000) break
                            delay(1_000)
                        }
                    }
                } catch (e: Exception) {
                    // HTTP 400, a JSON answer that does not fit, anything else: never let it escape this scope (crash)
                    errors += e.message ?: e.javaClass.simpleName
                    Log.w(TAG, "Gemini ($model): $e")
                } finally {
                    // this model is done without an answer: the next one, if it has not started yet
                    if (!answer.isCompleted) startNextModel()
                    if (running.decrementAndGet() == 0 && !answer.isCompleted) {
                        answer.completeExceptionally(
                            ScanException("Gemini nie odpowiedziało (${errors.joinToString("; ").ifEmpty { "brak czasu" }})"),
                        )
                    }
                }
            }
        }

        startNextModel()
        val hedge = launch {
            delay(hedgeAfterMs)
            if (!answer.isCompleted) {
                Log.i(TAG, "Gemini: ${models.first()} nie odpowiada od ${hedgeAfterMs / 1000} s, pytam też kolejny model")
                startNextModel()
            }
        }
        try {
            withTimeout(budgetMs) { answer.await() }
        } catch (e: TimeoutCancellationException) {
            throw ScanException("Gemini nie odpowiedziało w ${budgetMs / 1000} s (${errors.joinToString("; ")})")
        } finally {
            hedge.cancel()
        }
    }

    private fun readWith(model: String, jpeg: ByteArray, timeoutMs: Int): Pair<ScannedReceipt, Int> {
        val user = JSONObject()
            .put("role", "user")
            .put("parts", JSONArray()
                .put(JSONObject().put("inline_data", JSONObject()
                    .put("mime_type", "image/jpeg")
                    .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))))
                .put(JSONObject().put("text", ReceiptPrompt.IMAGE_USER_MESSAGE)))
        val contents = JSONArray().put(user)
        val (first, firstRaw) = ask(model, contents, timeoutMs)
        if (first.sumMatches || first.items.isEmpty() || first.totalGrosze == null) return first to 1

        // one correction round, like the backend: tell the model by how much its items are off
        contents
            .put(JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", firstRaw))))
            .put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject()
                .put("text", ReceiptPrompt.correctionRequest(first.itemsSumGrosze, first.totalGrosze)))))
        val second = try {
            ask(model, contents, timeoutMs).first
        } catch (e: ModelUnavailable) {
            return first to 2
        }
        return (if (second.items.isNotEmpty() && second.gap < first.gap) second else first) to 2
    }

    /** One generateContent call → (receipt, raw JSON text of the answer). */
    private fun ask(model: String, contents: JSONArray, timeoutMs: Int): Pair<ScannedReceipt, String> {
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", ReceiptPrompt.SYSTEM))))
            .put("contents", contents)
            .put("generation_config", JSONObject()
                .put("temperature", 0)
                .put("response_mime_type", "application/json")
                .put("response_json_schema", SCHEMA))

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        val (code, response) = try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            status to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } catch (e: SocketTimeoutException) {
            throw ModelUnavailable("$model nie odpowiedziało w ${timeoutMs / 1000} s", retryable = false)
        } catch (e: IOException) {
            throw ModelUnavailable("$model: brak połączenia (${e.message})", retryable = false)
        } finally {
            connection.disconnect()
        }
        if (code == 429 || code >= 500) {
            throw ModelUnavailable("$model: HTTP $code ${errorMessage(response)}", retryable = code == 429 || code == 503)
        }
        if (code == 404) throw ModelUnavailable("$model: HTTP 404 (nie ma takiego modelu)", retryable = false)
        if (code != 200) throw ScanException("Gemini ($model) zwróciło HTTP $code: ${response.take(300)}")

        val root = JSONObject(response)
        val parts = root.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw ScanException("Gemini nie zwróciło odpowiedzi.")
        val text = buildString {
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (!part.optBoolean("thought")) append(part.optString("text"))
            }
        }
        return parseAnswer(text) to text
    }

    private fun parseAnswer(text: String): ScannedReceipt = try {
        val json = JSONObject(text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```"))
        val array = json.optJSONArray("pozycje") ?: JSONArray()
        val lines = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            ModelLine(o.optString("typ"), o.optString("nazwa"), o.optDouble("kwota", 0.0))
        }
        ModelLines.toReceipt(json.optString("sklep"), json.optDouble("suma_calkowita", 0.0), lines, ScanSource.GEMINI)
    } catch (e: org.json.JSONException) {
        throw ScanException("Gemini zwróciło JSON niezgodny ze schematem.", e)
    }

    private class ModelUnavailable(message: String, val retryable: Boolean) : Exception(message)

    /** Google's error body is JSON; its "message" is enough for the log and the screen. */
    private fun errorMessage(body: String): String =
        runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()?.take(150) ?: body.take(150)

    companion object {
        private const val TAG = "FairShareScan"

        /** Same JSON Schema as the backend's RECEIPT_SCHEMA. */
        private val SCHEMA: JSONObject = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject()
                .put("sklep", JSONObject().put("type", "string"))
                .put("suma_calkowita", JSONObject().put("type", "number"))
                .put("pozycje", JSONObject()
                    .put("type", "array")
                    .put("items", JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject()
                            .put("typ", JSONObject().put("type", "string").put("enum", JSONArray(ModelLines.ALL)))
                            .put("nazwa", JSONObject().put("type", "string"))
                            .put("kwota", JSONObject().put("type", "number")))
                        .put("required", JSONArray(listOf("typ", "nazwa", "kwota"))))))
            .put("required", JSONArray(listOf("sklep", "suma_calkowita", "pozycje")))

        /** At most 2048 px on the long side, JPEG – what the vision models got in the evaluation. */
        private fun Bitmap.forVision(maxSide: Int = 2048): ByteArray {
            val longSide = maxOf(width, height)
            val scaled = if (longSide <= maxSide) this else {
                val scale = maxSide.toFloat() / longSide
                Bitmap.createScaledBitmap(this, (width * scale).roundToInt(), (height * scale).roundToInt(), true)
            }
            return ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.toByteArray()
            }
        }
    }
}
