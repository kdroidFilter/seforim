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
data class Commentator(
    val name: String,
    val path: String
)

@Serializable
data class TextWithRef(
    val text: String,
    val ref: String
)


@Serializable
sealed class CommentaryBase {
    abstract val commentator: Commentator
    abstract val texts: List<TextWithRef>
}

@Serializable
data class Commentary(
    override val commentator: Commentator,
    override val texts: List<TextWithRef>
) : CommentaryBase()

@Serializable
data class Targum(
    override val commentator: Commentator,
    override val texts: List<TextWithRef>
) : CommentaryBase()

@Serializable
data class QuotingCommentary(
    override val commentator: Commentator,
    override val texts: List<TextWithRef>
) : CommentaryBase()

@Serializable
data class Reference(
    override val commentator: Commentator,
    override val texts: List<TextWithRef>
) : CommentaryBase()

@Serializable
data class OtherLinks(
    override val commentator: Commentator,
    override val texts: List<TextWithRef>
) : CommentaryBase()

