package com.kdroid.seforim.core.model

import kotlinx.serialization.Serializable

// Nouvelles data classes pour l'index


@Serializable
enum class BookType {
    TALMUD, OTHER
}

@Serializable
data class BookSchema(
    val addressTypes: List<String>,
    val sectionNames: List<String>
)

@Serializable
data class BookIndex(
    val type: BookType = BookType.OTHER,
    val title: String,
    val heTitle: String,
    val numberOfChapters: Int,
    val chapters: List<ChapterIndex>,
    val description: String? = null,
    val sectionNames: List<String>
)

@Serializable
data class ChapterIndex(
    val chapterNumber: Int,
    val offset : Int,
    val numberOfVerses: Int,
    val commentators: List<String>
)
@Serializable
data class DirectoryNode(
    val englishName: String,
    val hebrewName: String?,
    val indexPath: String,
    val children: List<DirectoryNode> = emptyList(),
    val isLeaf: Boolean = false
)
