package com.whirlpool.app.ui

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal object FeedPreferenceCodec {
    fun encodeStringList(values: List<String>): String {
        return values
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(separator = "\n")
    }

    fun decodeStringList(value: String): List<String> {
        return value
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun encodeFilterSelection(selection: Map<String, Set<String>>): String {
        return selection.entries
            .mapNotNull { (optionId, selectedIds) ->
                val normalizedOptionId = optionId.trim()
                if (normalizedOptionId.isEmpty()) {
                    return@mapNotNull null
                }
                val normalizedChoices = selectedIds
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                normalizedOptionId to normalizedChoices
            }
            .sortedBy { (optionId, _) -> optionId }
            .joinToString(separator = "&") { (optionId, selectedIds) ->
                val encodedOptionId = urlEncodeToken(optionId)
                if (selectedIds.isEmpty()) {
                    "$encodedOptionId="
                } else {
                    val encodedChoices = selectedIds
                        .sorted()
                        .joinToString(separator = ",") { choiceId ->
                            urlEncodeToken(choiceId)
                        }
                    "$encodedOptionId=$encodedChoices"
                }
            }
    }

    fun decodeFilterSelection(value: String): Map<String, Set<String>> {
        if (value.isBlank()) {
            return emptyMap()
        }

        val out = linkedMapOf<String, Set<String>>()
        value.split("&")
            .forEach { entry ->
                val trimmed = entry.trim()
                if (trimmed.isEmpty()) {
                    return@forEach
                }

                val separator = trimmed.indexOf('=')
                val encodedOptionId = if (separator >= 0) {
                    trimmed.substring(0, separator)
                } else {
                    trimmed
                }
                val encodedChoices = if (separator >= 0) {
                    trimmed.substring(separator + 1)
                } else {
                    ""
                }

                val optionId = urlDecodeToken(encodedOptionId).trim()
                if (optionId.isEmpty()) {
                    return@forEach
                }

                val selectedIds = if (encodedChoices.isBlank()) {
                    emptySet()
                } else {
                    encodedChoices
                        .split(",")
                        .map { token -> urlDecodeToken(token).trim() }
                        .filter { it.isNotEmpty() }
                        .toSet()
                }
                out[optionId] = selectedIds
            }
        return out
    }

    private fun urlEncodeToken(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    }

    private fun urlDecodeToken(value: String): String {
        return runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
        }.getOrElse { value }
    }
}
