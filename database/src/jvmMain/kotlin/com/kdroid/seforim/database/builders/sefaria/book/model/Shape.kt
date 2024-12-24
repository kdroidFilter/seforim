package com.kdroid.seforim.database.builders.sefaria.book.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.Logger

// Structure retournée par l'endpoint "shape"
@Serializable
internal data class ShapeItem(
    val section: String,
    val heTitle: String,
    val title: String,
    val length: Int,
    val chapters: List<Int>,
    val book: String,
    val heBook: String,
)

@Serializable
internal data class ComplexShapeItem(
    val isComplex: Boolean = false,
    val section: String,
    val length: Int,
    val chapters: List<JsonElement>,
    val book: String,
    val heBook: String
)

@Serializable
internal data class FlexibleShapeItem(
    val section: String,
    val heTitle: String,
    val title: String,
    val length: Int,
    val chapters: JsonElement,
    val book: String,
    val heBook: String
) {

    fun toShapeItem(logger: Logger): ShapeItem {
        val chapList = mutableListOf<Int>()

        when (chapters) {
            is JsonArray -> {
                chapters.jsonArray.forEach { chapterElement ->
                    when (chapterElement) {
                        is JsonPrimitive -> {
                            // Si c'est un entier, ajoutez-le directement
                            chapterElement.jsonPrimitive.intOrNull?.let { chapList.add(it) }
                        }
                        is JsonArray -> {
                            // Si c'est une liste, calculez le nombre de versets
                            val verseCount = chapterElement.jsonArray.sumOf { verse ->
                                when (verse) {
                                    is JsonPrimitive -> verse.jsonPrimitive.intOrNull ?: 0
                                    else -> 0
                                }
                            }
                            chapList.add(verseCount)
                        }
                        else -> {
                            // Ignorer les autres types
                            logger.debug("Type inattendu dans les chapitres : ${chapterElement::class.simpleName}")
                        }
                    }
                }
            }
            is JsonPrimitive -> {
                // Si c'est un entier, ajoutez-le directement
                chapters.jsonPrimitive.intOrNull?.let { chapList.add(it) }
            }
            else -> {
                logger.debug("Format non supporté pour le champ 'chapters' dans '{}' : {}", title, chapters)
            }
        }

        // Filtrer les chapitres avec 0 versets si nécessaire
        val filteredChapList = chapList.filter { it > 0 }

        return ShapeItem(
            section = section,
            heTitle = heTitle,
            title = title,
            length = length,
            chapters = filteredChapList,
            book = book,
            heBook = heBook
        )
    }

}
