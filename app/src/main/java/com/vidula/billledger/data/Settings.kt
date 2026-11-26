package com.vidula.billledger.data

import android.content.Context

data class AppSettings(
    val currency: String = "AED",
    val excelUri: String = "",        // linked workbook that is rewritten after every change
    val excelName: String = "",
    val lastExcelSync: Long = 0,
)

/** Small wrapper over the app's private preferences. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    init {
        // Earlier builds could store an API key for online reading; that option is gone, so clear it.
        if (prefs.contains("apiKey")) prefs.edit().remove("apiKey").remove("readMode").remove("model").apply()
    }

    fun load() = AppSettings(
        currency = prefs.getString("currency", "AED") ?: "AED",
        excelUri = prefs.getString("excelUri", "") ?: "",
        excelName = prefs.getString("excelName", "") ?: "",
        lastExcelSync = prefs.getLong("lastExcelSync", 0),
    )

    fun save(s: AppSettings) {
        prefs.edit()
            .putString("currency", s.currency)
            .putString("excelUri", s.excelUri)
            .putString("excelName", s.excelName)
            .putLong("lastExcelSync", s.lastExcelSync)
            .apply()
    }
}
