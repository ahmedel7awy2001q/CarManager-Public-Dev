package com.ahmed.carmanager.ui

/**
 * Normalizes human-entered part queries without destroying real part/OEM numbers.
 *
 * Textual separators are spaces for better storefront recall (e.g. "brake-fluid" ->
 * "brake fluid"), while an alphanumeric token that contains at least one digit keeps its ASCII
 * hyphens (e.g. "26300-35505", "G4FG-123").
 *
 * Older search layers used to replace every ASCII hyphen with a space. To stay compatible with
 * those callers, this helper also repairs conservative OEM-shaped pairs such as
 * "26300 35505" or "28113 F2000" back to their hyphenated form. Ordinary year/spec pairs such as
 * "2021 1600" are deliberately left untouched.
 */
internal object PartQueryText {
    private val whitespace = Regex("\\s+")
    private val oemLikeToken = Regex("^[\\p{L}\\p{N}]+(?:-[\\p{L}\\p{N}]+)+$")
    private val asciiCodeSegment = Regex("^[A-Za-z0-9]+$")

    fun normalizeSeparators(raw: String): String {
        val normalizedTokens = raw
            .replace('—', ' ')
            .replace('–', ' ')
            .split(whitespace)
            .filter { it.isNotBlank() }
            .flatMap { token ->
                if (oemLikeToken.matches(token) && token.any { it.isDigit() }) {
                    listOf(token)
                } else {
                    token.replace('-', ' ')
                        .split(whitespace)
                        .filter { it.isNotBlank() }
                }
            }

        val normalized = repairLikelySplitOem(normalizedTokens)
            .joinToString(" ")
            .trim()
        return canonicalizeEgyptianStoreTerms(normalized)
    }

    /**
     * Storefront terminology is not always the same as the user's wording. Keep this list tiny and
     * semantic: only aliases that stay inside the exact same part family belong here.
     */
    private fun canonicalizeEgyptianStoreTerms(value: String): String = value
        .replace("سائل الفرامل", "زيت فرامل")
        .replace("سائل فرامل", "زيت فرامل")
        .replace("زيت الفرامل", "زيت فرامل")

    private fun repairLikelySplitOem(tokens: List<String>): List<String> {
        if (tokens.size < 2) return tokens
        val repaired = ArrayList<String>(tokens.size)
        var index = 0
        while (index < tokens.size) {
            val current = tokens[index]
            val next = tokens.getOrNull(index + 1)
            if (next != null && shouldJoinAsOem(current, next)) {
                repaired += "$current-$next"
                index += 2
            } else {
                repaired += current
                index += 1
            }
        }
        return repaired
    }

    private fun shouldJoinAsOem(left: String, right: String): Boolean {
        if (!isCodeSegment(left) || !isCodeSegment(right)) return false
        if (isLikelyYear(left) || isLikelyYear(right)) return false

        val hasLetter = left.any { it.isLetter() } || right.any { it.isLetter() }
        val hasLongSegment = left.length >= 5 || right.length >= 5
        return hasLetter || hasLongSegment
    }

    private fun isCodeSegment(value: String): Boolean =
        value.length in 3..12 && asciiCodeSegment.matches(value) && value.any { it.isDigit() }

    private fun isLikelyYear(value: String): Boolean =
        value.length == 4 && value.toIntOrNull()?.let { it in 1900..2100 } == true
}
