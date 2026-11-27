package com.roaddamage.data
import android.content.Context
import androidx.core.content.edit
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun get() = AppSettings(prefs.getBoolean("auto", true), prefs.getLong("interval", 1500), prefs.getFloat("threshold", .28f), prefs.getBoolean("wifi", false))
    fun save(value: AppSettings) = prefs.edit { putBoolean("auto", value.autoScan); putLong("interval", value.intervalMs); putFloat("threshold", value.confidenceThreshold); putBoolean("wifi", value.wifiOnly) }
    fun token() = prefs.getString("token", null)
    fun saveToken(token: String) = prefs.edit { putString("token", token) }
    fun role() = prefs.getString("role", "user") ?: "user"
    fun email() = prefs.getString("email", "") ?: ""
    fun saveSession(token: String, email: String, role: String) = prefs.edit {
        putString("token", token)
        putString("email", email)
        putString("role", role)
    }
    fun clearSession() = prefs.edit { remove("token"); remove("email"); remove("role") }
}
