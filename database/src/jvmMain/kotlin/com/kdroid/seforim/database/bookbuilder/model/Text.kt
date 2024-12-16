package com.kdroid.seforim.database.bookbuilder.model

import kotlinx.serialization.Serializable

// Structure retournée par l'endpoint texte
@Serializable
internal data class Version(
    val status: String? = null,
    val language: String? = null,
    val text: String? = null,
    val versionSource: String? = null
)

@Serializable
internal data class VerseResponse(
    val versions: List<Version> = emptyList()
)