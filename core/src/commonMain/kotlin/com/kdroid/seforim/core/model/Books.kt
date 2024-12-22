package com.kdroid.seforim.core.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
data class Book(
    val name: String,
    val chapters: List<Chapter>
)



@Serializable
data class Chapter(
    val number: Int,
    val verses: List<Verse>
)

@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
@Serializable
data class Verse(
    val number: Int,
    val text: String,
    val commentary: List<Commentary> = emptyList(),
    val targum: List<Targum> = emptyList(),
    val quotingCommentary: List<QuotingCommentary> = emptyList(),
    val reference: List<Reference> = emptyList(),
    val otherLinks: List<OtherLinks> = emptyList()
)