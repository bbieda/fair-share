package com.fairshare.data.model

/** Short code for a group that lives on this phone, e.g. "FS-A". */
object InviteCodes {
    fun format(groupId: Long): String = "FS-" + groupId.toString(36).uppercase()

    fun parse(raw: String): Long? {
        val code = raw.trim().uppercase()
            .removePrefix("FAIRSHARE")
            .trim()
            .removePrefix("FS-")
            .removePrefix("FS")
            .trim()
        if (code.isEmpty()) return null
        return code.toLongOrNull(36)?.takeIf { it > 0 }
    }
}
