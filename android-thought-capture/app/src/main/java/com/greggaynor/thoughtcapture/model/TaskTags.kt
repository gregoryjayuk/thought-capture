package com.greggaynor.thoughtcapture.model

object TaskTags {
    fun toStorageValue(value: String): String {
        return value
            .trim()
            .trimStart('@', '+', '#')
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-{2,}"), "-")
            .trim('-')
    }

    fun toCanonicalKey(value: String): String {
        return value
            .trim()
            .trimStart('@', '+', '#')
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .replace(Regex("-{2,}"), "-")
            .trim('-')
    }

    fun toDisplayValue(value: String): String {
        return value.trim().replace('-', ' ')
    }

    fun toDisplayLabel(prefix: String, value: String): String {
        return prefix + toDisplayValue(value)
    }
}
