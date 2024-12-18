package com.kdroid.seforim.database.builders.sefaria.book.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Structure retournée par l'endpoint texte
@Serializable
internal data class Version(
    val status: String? = null,
    val language: String? = null,
    val text: JsonElement, // Changement ici pour gérer JsonPrimitive et JsonArray
    val versionSource: String? = null
)


@Serializable
internal data class VerseResponse(
    val versions: List<Version> = emptyList()
)