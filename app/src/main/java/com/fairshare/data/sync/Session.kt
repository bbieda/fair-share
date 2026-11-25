package com.fairshare.data.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The logged-in account and the sync position, kept in private SharedPreferences. */
class Session(context: Context) {
    private val prefs = context.getSharedPreferences("fairshare_session", Context.MODE_PRIVATE)

    data class Account(val token: String, val email: String, val personUid: String)

    private val _account = MutableStateFlow(readAccount())
    /** null = nobody logged in (or the server ended the session): the app shows the login screen. */
    val account: StateFlow<Account?> = _account.asStateFlow()

    val token: String? get() = _account.value?.token

    /**
     * The person the local database belongs to. Kept after the token is gone, so logging in again to the same
     * account keeps the local data and the unsent changes, while another account starts from an empty database.
     */
    val dataOwnerUid: String? get() = prefs.getString(KEY_OWNER, null)

    /** Highest server version this phone has applied (see docs/chmura.md). */
    var cursor: Long
        get() = prefs.getLong(KEY_CURSOR, 0L)
        set(value) = prefs.edit().putLong(KEY_CURSOR, value).apply()

    /** Set when the server rejected a change: the next pull downloads everything and replaces the local copy. */
    var needsFullSync: Boolean
        get() = prefs.getBoolean(KEY_FULL, false)
        set(value) = prefs.edit().putBoolean(KEY_FULL, value).apply()

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    fun signIn(token: String, email: String, personUid: String) {
        if (dataOwnerUid != personUid) {
            prefs.edit().remove(KEY_CURSOR).remove(KEY_FULL).remove(KEY_LAST_SYNC).apply()
        }
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_EMAIL, email)
            .putString(KEY_OWNER, personUid)
            .apply()
        _account.value = Account(token, email, personUid)
    }

    /** The server no longer accepts the token: log in again; local data and unsent changes stay. */
    fun expire() {
        prefs.edit().remove(KEY_TOKEN).apply()
        _account.value = null
    }

    /** Logging out: everything about the account goes (the local database is cleared separately). */
    fun clear() {
        prefs.edit().clear().apply()
        _account.value = null
    }

    private fun readAccount(): Account? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val owner = prefs.getString(KEY_OWNER, null) ?: return null
        return Account(token, prefs.getString(KEY_EMAIL, null).orEmpty(), owner)
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_EMAIL = "email"
        const val KEY_OWNER = "person_uid"
        const val KEY_CURSOR = "cursor"
        const val KEY_FULL = "needs_full_sync"
        const val KEY_LAST_SYNC = "last_sync_at"
    }
}
