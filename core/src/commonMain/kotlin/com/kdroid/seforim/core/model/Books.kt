package com.kdroid.seforim.core.model

import kotlinx.serialization.Serializable

@Serializable
data class CommentaryResponse(
    val commentary: List<Commentary> = emptyList(),
    val quotingCommentary: List<QuotingCommentary> = emptyList(),
    val reference: List<Reference> = emptyList(),
    val otherLinks: List<OtherLinks> = emptyList()
)


@Serializable
sealed class CommentaryBase {
    abstract val commentatorName: String
    abstract val texts: List<String>
}

@Serializable
data class Commentary(
    override val commentatorName: String,
    override val texts: List<String>
) : CommentaryBase()

@Serializable
data class QuotingCommentary(
    override val commentatorName: String,
    override val texts: List<String>
) : CommentaryBase()

@Serializable
data class Reference(
    override val commentatorName: String,
    override val texts: List<String>
) : CommentaryBase()

@Serializable
data class OtherLinks(
    override val commentatorName: String,
    override val texts: List<String>
) : CommentaryBase()


@Serializable
data class Verse(
    val number: Int,
    val text: String,
    val commentary: List<Commentary> = emptyList(),
    val quotingCommentary: List<QuotingCommentary> = emptyList(),
    val reference: List<Reference> = emptyList(),
    val otherLinks: List<OtherLinks> = emptyList()
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