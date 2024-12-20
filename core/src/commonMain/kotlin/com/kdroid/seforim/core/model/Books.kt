package com.kdroid.seforim.core.model

import kotlinx.serialization.Serializable

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