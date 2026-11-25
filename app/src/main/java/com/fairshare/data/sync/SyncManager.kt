package com.fairshare.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.fairshare.data.AppSettings
import com.fairshare.data.db.ActivityLogEntity
import com.fairshare.data.db.ExpenseEntity
import com.fairshare.data.db.ExpenseItemEntity
import com.fairshare.data.db.ExpenseItemShareEntity
import com.fairshare.data.db.ExpenseShareEntity
import com.fairshare.data.db.FairShareDatabase
import com.fairshare.data.db.GroupEntity
import com.fairshare.data.db.GroupMemberEntity
import com.fairshare.data.db.PendingOpEntity
import com.fairshare.data.db.UserEntity
import com.fairshare.ui.notifyIfEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** What the person joining a group sees before confirming: the group and the people added by name in it. */
data class JoinPreview(
    val groupName: String,
    val memberNames: List<String>,
    val alreadyMember: Boolean,
    /** uid -> name of people without an account: the joining person may be one of them. */
    val placeholders: List<Pair<String, String>>,
)

/**
 * Keeps the local database in step with the server (docs/chmura.md).
 *
 * A sync is: send the queue of local changes (push), then download what changed since [Session.cursor] (pull)
 * and apply it. The download is applied only while the queue is empty, so it never overwrites a local change
 * the server has not seen yet. Every server call goes through one mutex: logging in, syncing and joining never
 * run at the same time.
 */
class SyncManager(
    private val context: Context,
    private val db: FairShareDatabase,
    private val session: Session,
    private val api: FairShareApi,
    private val settings: AppSettings,
) {
    enum class State { IDLE, SYNCING, OFFLINE, ERROR }

    data class Status(val state: State = State.IDLE, val lastSyncAt: Long = 0L, val message: String? = null)

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val pendingOps = db.pendingOpDao()

    private val _status = MutableStateFlow(Status(lastSyncAt = session.lastSyncAt))
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Changes saved on this phone and not confirmed by the server yet. */
    val pendingCount: Flow<Int> = pendingOps.observeCount()

    val serverConfigured: Boolean get() = api.isConfigured

    val account: StateFlow<Session.Account?> get() = session.account

    init {
        // Local changes call requestSync() one after another; they collapse into one sync at a time
        scope.launch { for (request in requests) syncNow() }
    }

    /** Sync soon, in the background (after a local change, when the network comes back...). */
    fun requestSync() {
        requests.trySend(Unit)
    }

    /** Syncs now. Returns false when offline or the server refused; [status] says why. */
    suspend fun syncNow(): Boolean = mutex.withLock { syncLocked() }

    // ---------- account ----------

    suspend fun register(email: String, password: String, name: String) =
        signIn("register", json("email" to email.trim(), "password" to password, "name" to name.trim()))

    suspend fun login(email: String, password: String) =
        signIn("login", json("email" to email.trim(), "password" to password))

    private suspend fun signIn(action: String, body: JSONObject) {
        mutex.withLock {
            val answer = api.call(action, body)
            val person = answer.getJSONObject("person")
            val personUid = person.getString("id")
            if (session.dataOwnerUid != personUid) {
                // another account (or the first login on this phone): its data must not mix with the previous one's
                withContext(Dispatchers.IO) { db.clearAllTables() }
            }
            // "me" first: the app replaces the login screen as soon as the session is set
            db.withTransaction { upsertPerson(person, personUid) }
            session.signIn(answer.getString("token"), answer.optString("email"), personUid)
            syncLocked()
        }
    }

    /** Ends the session on the server (when reachable) and removes everything of this account from the phone. */
    suspend fun logout() {
        mutex.withLock {
            try {
                api.call("logout")
            } catch (_: Exception) {
                // offline or already expired: the token is forgotten on the phone anyway
            }
            withContext(Dispatchers.IO) { db.clearAllTables() }
            session.clear()
            _status.value = Status()
        }
    }

    // ---------- joining a group ----------

    suspend fun joinPreview(code: String): JoinPreview = mutex.withLock {
        val answer = api.call("join_preview", json("code" to code))
        val group = answer.getJSONObject("group")
        val placeholders = mutableListOf<Pair<String, String>>()
        answer.getJSONArray("placeholders").forEachObject { placeholders += it.getString("id") to it.getString("name") }
        val names = group.getJSONArray("member_names")
        JoinPreview(
            groupName = group.getString("name"),
            memberNames = (0 until names.length()).map { names.getString(it) },
            alreadyMember = answer.optBoolean("already_member"),
            placeholders = placeholders,
        )
    }

    /**
     * Joins the group with [code], as the person [claimPersonUid] when given (someone added by name), and
     * downloads it. Returns the local id of the group.
     */
    suspend fun join(code: String, claimPersonUid: String?): Long = mutex.withLock {
        val answer = api.call("join", json("code" to code, "claim_person_id" to claimPersonUid))
        val groupUid = answer.getString("group_id")
        syncLocked()
        db.groupDao().getByUid(groupUid)?.id
            ?: throw IOException("Grupa pobierze się przy następnej synchronizacji")
    }

    // ---------- sync ----------

    private suspend fun syncLocked(): Boolean {
        if (session.account.value == null || !api.isConfigured) return false
        _status.value = _status.value.copy(state = State.SYNCING, message = null)
        return try {
            // A change made while downloading puts something in the queue again: then push and pull once more
            repeat(MAX_ROUNDS) {
                push()
                val since = if (session.needsFullSync) 0L else session.cursor
                val answer = api.call("pull", json("since" to since))
                val outcome = db.withTransaction {
                    if (pendingOps.count() > 0) null else applyPull(answer)
                }
                if (outcome != null) {
                    session.cursor = answer.getLong("cursor")
                    session.needsFullSync = false
                    session.lastSyncAt = System.currentTimeMillis()
                    _status.value = Status(State.IDLE, session.lastSyncAt)
                    notifyAbout(outcome)
                    return true
                }
            }
            _status.value = Status(State.IDLE, session.lastSyncAt)
            false
        } catch (e: IOException) {
            _status.value = Status(State.OFFLINE, session.lastSyncAt, "Brak połączenia z serwerem")
            false
        } catch (e: ApiException) {
            if (e.status == 401) session.expire()
            _status.value = Status(State.ERROR, session.lastSyncAt, e.message)
            false
        }
    }

    /** Sends the queue in batches. Changes the server rejected are dropped and the next pull is a full one. */
    private suspend fun push() {
        while (true) {
            val ops = pendingOps.first(PUSH_BATCH)
            if (ops.isEmpty()) return
            val body = json("ops" to jsonArray(ops) { op ->
                json("op_id" to op.id, "type" to op.type, "data" to JSONObject(op.payload))
            })
            val results = api.call("push", body).getJSONArray("results")
            val answered = mutableListOf<Long>()
            val rejected = mutableListOf<String>()
            results.forEachObject { result ->
                answered += result.getLong("op_id")
                if (!result.optBoolean("ok")) rejected += result.optString("error")
            }
            db.withTransaction {
                pendingOps.delete(answered)
                rejected.distinct().forEach { reason ->
                    // only on this phone: tells why something the user saved disappears after the sync
                    db.activityLogDao().insert(
                        ActivityLogEntity(actionType = "SYNC_REJECTED", description = "Serwer nie przyjął zmiany: $reason")
                    )
                }
            }
            if (rejected.isNotEmpty()) session.needsFullSync = true
            if (answered.isEmpty()) throw ApiException(500, "Serwer nie potwierdził zmian")
        }
    }

    private class PullOutcome(val full: Boolean, val newActivity: List<Pair<String?, String>>)

    /** Writes one pull answer into the database. Runs inside a transaction, with the queue empty. */
    private suspend fun applyPull(answer: JSONObject): PullOutcome {
        val full = answer.optBoolean("full")
        val myUid = answer.getString("me")
        val users = db.userDao()
        val groups = db.groupDao()
        val expenses = db.expenseDao()
        val activity = db.activityLogDao()

        val userIds = HashMap<String, Long>()
        suspend fun userId(uid: String): Long? = userIds[uid] ?: users.getByUid(uid)?.id?.also { userIds[uid] = it }
        val groupIds = HashMap<String, Long>()
        suspend fun groupId(uid: String): Long? = groupIds[uid] ?: groups.getByUid(uid)?.id?.also { groupIds[uid] = it }

        answer.getJSONArray("persons").forEachObject { person ->
            userIds[person.getString("id")] = upsertPerson(person, myUid)
        }

        val seenGroups = HashSet<String>()
        answer.getJSONArray("groups").forEachObject { g ->
            val uid = g.getString("id")
            seenGroups += uid
            val existing = groups.getByUid(uid)
            groupIds[uid] = if (existing != null) {
                groups.update(
                    existing.copy(name = g.getString("name"), archived = g.getBoolean("archived"), inviteCode = g.stringOrNull("invite_code"))
                )
                existing.id
            } else {
                groups.insert(
                    GroupEntity(
                        uid = uid,
                        name = g.getString("name"),
                        archived = g.getBoolean("archived"),
                        inviteCode = g.stringOrNull("invite_code"),
                        createdAt = g.getLong("created_at"),
                    )
                )
            }
        }

        val seenMembers = HashSet<Pair<Long, Long>>()
        answer.getJSONArray("members").forEachObject { m ->
            val groupId = groupId(m.getString("group_id")) ?: return@forEachObject
            val userId = userId(m.getString("person_id")) ?: return@forEachObject
            if (m.getBoolean("deleted")) {
                groups.deleteMember(groupId, userId)
            } else {
                seenMembers += groupId to userId
                groups.insertMember(GroupMemberEntity(groupId = groupId, userId = userId, joinedAt = m.getLong("joined_at")))
            }
        }

        val seenExpenses = HashSet<String>()
        answer.getJSONArray("expenses").forEachObject { e ->
            val uid = e.getString("id")
            val existing = expenses.getByUid(uid)
            if (e.getBoolean("deleted")) {
                existing?.let { expenses.deleteExpense(it.id) }
                return@forEachObject
            }
            seenExpenses += uid
            val groupId = groupId(e.getString("group_id"))
            val payerId = userId(e.getString("payer_id"))
            val shares = readShares(e.getJSONArray("shares")) { userId(it) }
            val items = e.getJSONArray("items")
            val itemShares = (0 until items.length()).map { readShares(items.getJSONObject(it).getJSONArray("shares")) { uid -> userId(uid) } }
            if (groupId == null || payerId == null || shares == null || itemShares.any { it == null }) {
                // refers to someone this phone does not know: take everything again next time
                session.needsFullSync = true
                return@forEachObject
            }
            val row = ExpenseEntity(
                id = existing?.id ?: 0,
                uid = uid,
                groupId = groupId,
                payerId = payerId,
                amount = e.getLong("amount"),
                description = e.getString("description"),
                category = e.stringOrNull("category"),
                date = e.getLong("day"),
                isSettlement = e.getBoolean("is_settlement"),
                createdAt = e.getLong("created_at"),
            )
            val expenseId = if (existing != null) {
                expenses.updateExpense(row)
                expenses.deleteShares(existing.id)
                expenses.deleteItems(existing.id)
                existing.id
            } else {
                expenses.insertExpense(row)
            }
            expenses.insertShares(shares!!.map { (userId, amount) -> ExpenseShareEntity(expenseId, userId, amount) })
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val itemId = expenses.insertItem(
                    ExpenseItemEntity(expenseId = expenseId, name = item.getString("name"), price = item.getLong("price"), position = item.getInt("position"))
                )
                expenses.insertItemShares(itemShares[i]!!.map { (userId, amount) -> ExpenseItemShareEntity(itemId, userId, amount) })
            }
        }

        if (full) {
            // The answer holds everything the account can see: whatever else is here was never accepted
            groups.getAll().filter { it.uid !in seenGroups }.forEach { groups.deleteGroup(it.id) }
            groups.getAllMembers().filter { (it.groupId to it.userId) !in seenMembers }.forEach { groups.deleteMember(it.groupId, it.userId) }
            expenses.getAllIds().filter { it.uid !in seenExpenses }.forEach { expenses.deleteExpense(it.id) }
        }

        // The first download of an account: its history is not news
        val historyOnly = full && session.cursor == 0L
        val news = mutableListOf<Pair<String?, String>>()
        answer.getJSONArray("activity").forEachObject { a ->
            val uid = a.getString("id")
            if (activity.countByUid(uid) > 0) return@forEachObject
            val actorUid = a.getString("actor_id")
            val mine = actorUid == myUid
            val groupId = a.stringOrNull("group_id")?.let { groupId(it) }
            val involves = a.getJSONArray("involves")
            val involvesMe = a.isNull("group_id") || (0 until involves.length()).any { involves.getString(it) == myUid }
            val actorName = userId(actorUid)?.let { users.getById(it)?.name }
            val description = if (mine || actorName == null) a.getString("description") else "$actorName: ${a.getString("description")}"
            activity.insert(
                ActivityLogEntity(
                    uid = uid,
                    groupId = groupId,
                    actionType = a.getString("type"),
                    description = description,
                    seen = mine || historyOnly,
                    timestamp = a.getLong("created_at"),
                    involvesMe = involvesMe,
                )
            )
            if (!mine && !historyOnly) {
                news += groupId?.let { groups.getGroup(it)?.name } to description
            }
        }
        return PullOutcome(full, news)
    }

    /** Inserts or updates a person from the server; returns the local id. The profile photo stays local. */
    private suspend fun upsertPerson(person: JSONObject, myUid: String): Long {
        val users = db.userDao()
        val uid = person.getString("id")
        val existing = users.getByUid(uid)
        val row = UserEntity(
            id = existing?.id ?: 0,
            uid = uid,
            name = person.getString("name"),
            isMe = uid == myUid,
            photoUri = existing?.photoUri,
            paymentDetails = person.stringOrNull("payment_details"),
            defaultCurrency = person.optString("default_currency", "PLN"),
            hasAccount = person.optBoolean("has_account"),
            createdAt = person.optLong("created_at", System.currentTimeMillis()),
        )
        return if (existing != null) {
            users.update(row)
            existing.id
        } else {
            users.insert(row)
        }
    }

    /** person uid -> grosze as local user id -> grosze; null when a person is unknown here. */
    private suspend fun readShares(list: JSONArray, userId: suspend (String) -> Long?): Map<Long, Long>? {
        val result = LinkedHashMap<Long, Long>()
        for (i in 0 until list.length()) {
            val share = list.getJSONObject(i)
            val id = userId(share.getString("person_id")) ?: return null
            result[id] = (result[id] ?: 0L) + share.getLong("amount")
        }
        return result
    }

    private fun notifyAbout(outcome: PullOutcome) {
        val news = outcome.newActivity
        if (news.isEmpty()) return
        if (news.size == 1) {
            val (group, text) = news.first()
            context.notifyIfEnabled(settings, group ?: "Fair Share", text)
        } else {
            context.notifyIfEnabled(settings, "Fair Share", "${news.size} nowe zmiany w Twoich grupach")
        }
    }

    private companion object {
        const val PUSH_BATCH = 100
        const val MAX_ROUNDS = 5
    }
}

/** Saves a change for the server in the same transaction as the local write (see [FairShareRepository]). */
internal suspend fun FairShareDatabase.enqueue(type: String, data: JSONObject) {
    pendingOpDao().insert(PendingOpEntity(type = type, payload = data.toString()))
}
