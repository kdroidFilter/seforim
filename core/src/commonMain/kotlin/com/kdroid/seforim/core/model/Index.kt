package com.kdroid.seforim.core.model

import kotlinx.serialization.Serializable

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