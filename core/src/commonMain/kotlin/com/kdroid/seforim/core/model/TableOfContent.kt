package com.kdroid.seforim.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TableOfContent(
    @SerialName("contents") val contents: List<ContentItem> = emptyList(),
    @SerialName("order") var order: Double? = null,
    @SerialName("enComplete") val enComplete: Boolean? = null,
    @SerialName("heComplete") val heComplete: Boolean? = null,
    @SerialName("enDesc") val enDesc: String? = null,
    @SerialName("heDesc") val heDesc: String? = null,
    @SerialName("enShortDesc") val enShortDesc: String? = null,
    @SerialName("heShortDesc") val heShortDesc: String? = null,
    @SerialName("heCategory") val heCategory: String? = null,
    @SerialName("category") val category: String? = null
)

@Serializable
data class ContentItem(
    @SerialName("contents") val contents: List<ContentItem>? = null,
    @SerialName("categories") val categories: List<String>? = null,
    @SerialName("order") var order: Double? = null,
    @SerialName("primary_category") val primaryCategory: String? = null,
    @SerialName("enShortDesc") val enShortDesc: String? = null,
    @SerialName("heShortDesc") val heShortDesc: String? = null,
    @SerialName("corpus") val corpus: String? = null,
    @SerialName("heTitle") val heTitle: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("enComplete") val enComplete: Boolean? = null,
    @SerialName("heComplete") val heComplete: Boolean? = null,
    @SerialName("enDesc") val enDesc: String? = null,
    @SerialName("heDesc") val heDesc: String? = null,
    @SerialName("heCategory") val heCategory: String? = null,
    @SerialName("category") val category: String? = null
)
