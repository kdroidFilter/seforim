package com.kdroid.seforim.database.builders.sefaria.book.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Structure retournée par l'endpoint links
@Serializable
internal data class CommentItem(
    val _id: String,
    val index_title: String,
    val category: String,
    val type: String? = null,
    val ref: String,
    val anchorRef: String,
    val anchorRefExpanded: List<String>,
    val sourceRef: String,
    val sourceHeRef: String,
    val anchorVerse: Int,
    val sourceHasEn: Boolean,
    val compDate: List<Int>? = null,
    val commentaryNum: Double,
    val collectiveTitle: CollectiveTitle,
    val heTitle: String? = null,
    val he: JsonElement? = null,
    val text: JsonElement? = null
)

@Serializable
internal data class CollectiveTitle(
    val en: String?,
    val he: String?
)
