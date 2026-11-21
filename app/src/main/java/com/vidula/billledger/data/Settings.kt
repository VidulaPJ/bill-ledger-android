package com.vidula.billledger.data

import android.content.Context

enum class ReadMode { ON_DEVICE, CLAUDE }

data class AppSettings(
    val currency: String = "AED",
    val readMode: ReadMode = ReadMode.ON_DEVICE,
    val apiKey: String = "",
    val model: String = "claude-sonnet-5-5",
    val excelUri: String = "",        // linked workbook that is rewritten after every change
    val excelName: String = "",
    val lastExcelSync: Long = 0,
)

/** Small wrapper over the app's private preferences. The API key never leaves the phone except to api.anthropic.com. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load() = AppSettings(
        currency = prefs.getString("currency", "AED") ?: "AED",
        readMode = runCatching { ReadMode.valueOf(prefs.getString("readMode", "ON_DEVICE")!!) }.getOrDefault(ReadMode.ON_DEVICE),
        apiKey = prefs.getString("apiKey", "") ?: "",
        model = prefs.getString("model", "claude-sonnet-5-5") ?: "claude-sonnet-5-5",
        excelUri = prefs.getString("excelUri", "") ?: "",
        excelName = prefs.getString("excelName", "") ?: "",
        lastExcelSync = prefs.getLong("lastExcelSync", 0),
    )

    fun save(s: AppSettings) {
        prefs.edit()
            .putString("currency", s.currency)
            .putString("readMode", s.readMode.name)
            .putString("apiKey", s.apiKey)
            .putString("model", s.model)
            .putString("excelUri", s.excelUri)
            .putString("excelName", s.excelName)
            .putLong("lastExcelSync", s.lastExcelSync)
            .apply()
    }
}
