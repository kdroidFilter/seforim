package com.kdroid.seforim.database.bookbuilder.model

import kotlinx.serialization.Serializable

// Structure retournée par l'endpoint "shape"
@Serializable
internal data class ShapeItem(
    val section: String,
    val heTitle: String,
    val title: String,
    val length: Int,
    val chapters: List<Int>,
    val book: String,
    val heBook: String
)
