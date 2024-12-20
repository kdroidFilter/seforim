package com.kdroid.seforim.core.model

import kotlinx.serialization.Serializable

enum class CommentaryType {
    COMMENTARY,
    TARGUM,
    QUOTING_COMMENTARY,
    REFERENCE,
    OTHER_LINKS
}

@Serializable
data class CommentaryResponse(
    val commentary: List<Commentary> = emptyList(),
    val targum: List<Targum> = emptyList(),
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
data class Targum(
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
