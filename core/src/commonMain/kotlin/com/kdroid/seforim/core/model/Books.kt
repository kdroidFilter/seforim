package com.kdroid.seforim.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Commentary(
    val commentatorName: String,
    val texts: List<String>
)

@Serializable
data class Verse(
    val number: Int,
    val text: String,
    val commentaries: List<Commentary> = emptyList()
)

@Serializable
data class Chapter(
    val number: Int,
    val verses: List<Verse>
)

@Serializable
data class Book(
    val name: String,
    val chapters: List<Chapter>
)

// Nouvelles data classes pour l'index
@Serializable
data class ChapterIndex(
    val chapterNumber: Int,
    val numberOfVerses: Int,
    val commentators: List<String>
)

@Serializable
data class BookIndex(
    val title: String,
    val heTitle: String,
    val numberOfChapters: Int,
    val chapters: List<ChapterIndex>,
    val description: String? = null
)


@Serializable
data class DirectoryNode(
    val name: String,
    val path: String,
    val children: List<DirectoryNode> = emptyList(),
    val isLeaf: Boolean = false,
    val hebrewTitle: String? = null
)