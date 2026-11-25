package com.fairshare.data.model

import java.security.SecureRandom

/**
 * Group invite codes, e.g. "K7MQ-2XPA": 8 random characters without 0/O and 1/I/L, so a code read aloud or
 * retyped from a screenshot still works. Same alphabet and length as server/api/api.php, which keeps the code
 * the phone proposes unless another group already has it.
 */
object InviteCodes {
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val LENGTH = 8
    private val random = SecureRandom()

    fun generate(): String = buildString { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** "K7MQ2XPA" -> "K7MQ-2XPA" for showing. */
    fun format(code: String): String = if (code.length == LENGTH) code.substring(0, 4) + "-" + code.substring(4) else code

    /** What the user typed -> the stored form, or null when it cannot be a code. */
    fun normalize(raw: String): String? =
        raw.uppercase().filter { it.isLetterOrDigit() }.takeIf { code -> code.length == LENGTH && code.all { it in ALPHABET } }
}
