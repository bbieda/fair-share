package com.fairshare.data.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * Kinds of changes in the offline queue. Each name matches a handler in server/api/api.php (OPS) and its
 * payload the fields that handler reads. Ids in payloads are the shared `uid`s, never local ids.
 */
object Ops {
    const val CREATE_GROUP = "create_group"
    const val UPDATE_GROUP = "update_group"
    const val ADD_PERSON = "add_person"
    const val ADD_MEMBER = "add_member"
    const val ADD_EXPENSE = "add_expense"
    const val DELETE_EXPENSE = "delete_expense"
    const val UPDATE_PROFILE = "update_profile"
    const val UPDATE_PERSON = "update_person"
    const val ADD_ACTIVITY = "add_activity"
}

/** JSON object from pairs; null values become JSON null (org.json would drop the key). */
internal fun json(vararg pairs: Pair<String, Any?>): JSONObject =
    JSONObject().apply { pairs.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) } }

internal fun <T> jsonArray(items: Iterable<T>, map: (T) -> Any?): JSONArray =
    JSONArray().apply { items.forEach { put(map(it) ?: JSONObject.NULL) } }

/** Like optString, but JSON null and a missing key give null instead of "null"/"". */
internal fun JSONObject.stringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key)

internal inline fun JSONArray.forEachObject(action: (JSONObject) -> Unit) {
    for (i in 0 until length()) action(getJSONObject(i))
}
