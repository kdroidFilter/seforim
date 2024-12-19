package com.kdroid.seforim.database.builders.sefaria.book.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Structure returned by the text endpoint
@Serializable
internal data class Version(
    val status: String? = null,
    val language: String? = null,
    val text: JsonElement, // Handling JsonPrimitive and JsonArray
    val versionSource: String? = null
)


@Serializable
internal data class VerseResponse(
    val versions: List<Version> = emptyList()
)