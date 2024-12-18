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
    val chapters: JsonElement, // Peut être un nombre, une liste d'entiers ou une liste de listes
    val book: String,
    val heBook: String
) {
    fun toShapeItem(logger: Logger): ShapeItem {
        return when {
            chapters is JsonArray -> {
                val chapList = chapters.jsonArray.mapNotNull { element ->
                    when (element) {
                        is JsonPrimitive -> {
                            val chapterCount = element.intOrNull
                            if (chapterCount == null) {
                                logger.debug("Élément de chapitre non entier: {}", element)
                            }
                            chapterCount
                        }
                        is JsonArray -> {
                            val sum = element.jsonArray.mapNotNull { it.jsonPrimitive.intOrNull }.sum()
                            if (sum == 0) {
                                logger.debug("Liste de chapitres avec somme de versets 0: {}", element)
                            }
                            sum.takeIf { it > 0 }
                        }
                        else -> {
                            logger.debug("Type d'élément de chapitre inattendu: ${element::class.simpleName}")
                            null
                        }
                    }
                }
                ShapeItem(section, heTitle, title, length, chapList, book, heBook)
            }
            chapters is JsonPrimitive && chapters.jsonPrimitive.intOrNull != null -> {
                val singleChapter = chapters.jsonPrimitive.int
                // Convertir en liste d’un seul élément
                ShapeItem(section, heTitle, title, length, listOf(singleChapter), book, heBook)
            }
            else -> {
                logger.debug("Format du champ 'chapters' non supporté pour '{}': {}", title, chapters)
                ShapeItem(section, heTitle, title, length, emptyList(), book, heBook) // Retourne une liste vide ou adaptez selon vos besoins
            }
        }
    }
}
