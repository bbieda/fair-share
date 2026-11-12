package com.fairshare.data

import android.content.Context
import com.fairshare.data.model.ExpenseCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Preferences that are not part of the ledger: theme, notifications, budget, saved custom categories. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("fairshare_settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(prefs.getString(KEY_THEME, THEME_DARK) ?: THEME_DARK)
    val themeMode: StateFlow<String> = _theme.asStateFlow()

    private val _notifications = MutableStateFlow(prefs.getBoolean(KEY_NOTIF, false))
    val notificationsEnabled: StateFlow<Boolean> = _notifications.asStateFlow()

    private val _budget = MutableStateFlow(prefs.getLong(KEY_BUDGET, 0L))
    val monthlyBudgetGrosze: StateFlow<Long> = _budget.asStateFlow()

    private val _categories = MutableStateFlow(readCategories())
    val customCategories: StateFlow<List<String>> = _categories.asStateFlow()

    fun setTheme(mode: String) {
        prefs.edit().putString(KEY_THEME, mode).apply()
        _theme.value = mode
    }

    fun setNotifications(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIF, enabled).apply()
        _notifications.value = enabled
    }

    fun setMonthlyBudget(grosze: Long) {
        val value = grosze.coerceAtLeast(0L)
        prefs.edit().putLong(KEY_BUDGET, value).apply()
        _budget.value = value
    }

    fun addCustomCategory(name: String) {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return
        if (ExpenseCategory.entries.any { it.label.equals(clean, ignoreCase = true) }) return
        val next = (_categories.value + clean).distinctBy { it.lowercase() }.sorted()
        writeCategories(next)
    }

    fun removeCustomCategory(name: String) {
        writeCategories(_categories.value.filterNot { it.equals(name, ignoreCase = true) })
    }

    /** Clears budget and custom categories (used by "delete all data"). Theme and notifications stay. */
    fun resetData() {
        prefs.edit().remove(KEY_BUDGET).remove(KEY_CATS).apply()
        _budget.value = 0L
        _categories.value = emptyList()
    }

    private fun readCategories(): List<String> =
        prefs.getStringSet(KEY_CATS, emptySet()).orEmpty().toList().sorted()

    private fun writeCategories(categories: List<String>) {
        prefs.edit().putStringSet(KEY_CATS, categories.toSet()).apply()
        _categories.value = categories
    }

    companion object {
        const val THEME_DARK = "DARK"
        const val THEME_LIGHT = "LIGHT"
        const val THEME_SYSTEM = "SYSTEM"

        private const val KEY_THEME = "theme"
        private const val KEY_NOTIF = "notifications"
        private const val KEY_BUDGET = "monthly_budget"
        private const val KEY_CATS = "custom_categories"
    }
}
