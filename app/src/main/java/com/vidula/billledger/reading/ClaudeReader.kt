package com.vidula.billledger.reading

import android.graphics.Bitmap
import android.util.Base64
import com.vidula.billledger.data.CATEGORIES
import com.vidula.billledger.data.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ClaudeException(message: String) : Exception(message)

/** Reads a bill by sending the page images to the Claude Messages API with the user's own key. */
object ClaudeReader {
    private val PROMPT = """
        You read images of bills, invoices and receipts. Return ONLY one JSON object, no prose:
        {"vendor":string,"date":"YYYY-MM-DD" or null,"due_date":"YYYY-MM-DD" or null,
         "total":number (final amount payable, after tax),"currency":ISO 4217 code or null,
         "category":one of ${JSONArray(CATEGORIES)},
         "items":[{"name":product text as printed,
                   "item":short generic item name in English, Title Case, brand-free (e.g. "Almarai Full Fat Milk 2L" -> "Milk", "Fresh Eggs 30s" -> "Eggs"; for service bills use the service, e.g. "Electricity", "Water", "Internet"),
                   "qty":total quantity as a number in the unit below (2 x 1L bottles -> 2; a 2L bottle -> 2; 500 g -> 0.5; 30 eggs -> 30),
                   "unit":one of "L","kg","pcs","service",
                   "amount":line total paid as a number}],
         "notes":short string such as billing period or account number last digits, or ""}
        List every purchased line (at most 60). Skip subtotal, discount, tax/VAT, change and payment lines.
        Dates on bills from the UAE, UK and India are usually day-first.
        If the images are not a bill or receipt, return {"error":"not a bill"}. Several images are pages of the same bill.
    """.trimIndent()

    suspend fun read(pages: List<Bitmap>, apiKey: String, model: String, defaultCurrency: String): Draft = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw ClaudeException("Add your Claude API key in Settings")
        val content = JSONArray()
        pages.forEach { bmp ->
            content.put(JSONObject().put("type", "image").put("source", JSONObject()
                .put("type", "base64").put("media_type", "image/jpeg")
                .put("data", Base64.encodeToString(bmp.toJpeg(), Base64.NO_WRAP))))
        }
        content.put(JSONObject().put("type", "text").put("text", PROMPT))
        val body = JSONObject()
            .put("model", model.ifBlank { "claude-sonnet-5-5" })
            .put("max_tokens", 4096)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))

        val conn = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", apiKey.trim())
            setRequestProperty("anthropic-version", "2023-06-01")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: ""
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrDefault("")
                throw ClaudeException(when (code) {
                    401 -> "Claude didn't accept the API key — check it in Settings"
                    429 -> "Too many requests — wait a minute and try again"
                    529, 503 -> "Claude is busy — try again shortly"
                    else -> "Claude error $code ${msg.take(80)}"
                })
            }
            val reply = JSONObject(text).getJSONArray("content")
            val out = StringBuilder()
            for (i in 0 until reply.length()) reply.getJSONObject(i).optString("text").let(out::append)
            parse(out.toString(), defaultCurrency)
        } finally {
            conn.disconnect()
        }
    }

    internal fun parse(text: String, defaultCurrency: String): Draft {
        val a = text.indexOf('{')
        val b = text.lastIndexOf('}')
        if (a < 0 || b <= a) throw ClaudeException("Couldn't understand Claude's reply")
        val j = JSONObject(text.substring(a, b + 1))
        if (j.has("error")) throw NotABillException()
        val items = mutableListOf<Item>()
        j.optJSONArray("items")?.let { arr ->
            for (i in 0 until arr.length()) {
                val x = arr.optJSONObject(i) ?: continue
                items += Item(
                    item = x.optString("item", x.optString("name")),
                    name = x.optString("name"),
                    qty = x.optDouble("qty", 1.0).takeIf { !it.isNaN() } ?: 1.0,
                    unit = x.optString("unit", "pcs"),
                    amount = x.optDouble("amount", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                )
            }
        }
        fun str(k: String) = if (j.isNull(k)) "" else j.optString(k)
        return cleanDraft(Draft(
            vendor = str("vendor"),
            date = str("date"),
            due = str("due_date"),
            total = j.optDouble("total", 0.0).takeIf { !it.isNaN() } ?: items.sumOf { it.amount },
            currency = str("currency").ifBlank { defaultCurrency },
            category = str("category"),
            items = items,
            notes = str("notes"),
        ), defaultCurrency)
    }
}
