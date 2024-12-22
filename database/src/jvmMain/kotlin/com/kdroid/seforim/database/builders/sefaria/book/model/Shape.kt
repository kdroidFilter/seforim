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
        return when {
            chapters is JsonArray -> {
                // Vérifier si chaque élément de 'chapters' est un JsonPrimitive (structure simple)
                if (chapters.jsonArray.all { it is JsonPrimitive && it.jsonPrimitive.intOrNull != null }) {
                    // Structure simple : liste d'entiers représentant le nombre de versets par chapitre
                    val chapList = chapters.jsonArray.mapNotNull { it.jsonPrimitive.intOrNull }
                        .filter { it > 0 } // Filtrer les chapitres avec 0 versets si nécessaire
                    ShapeItem(section, heTitle, title, length, chapList, book, heBook)
                }
                // Vérifier si chaque élément de 'chapters' est un JsonArray (structure complexe)
                else if (chapters.jsonArray.all { it is JsonArray }) {
                    // Structure complexe : liste de listes représentant les versets dans chaque chapitre
                    val chapList = chapters.jsonArray.map { chapterElement ->
                        if (chapterElement is JsonArray) {
                            chapterElement.size // Nombre de versets dans le chapitre
                        } else {
                            logger.debug("Type inattendu dans les chapitres : ${chapterElement::class.simpleName}")
                            0
                        }
                    }.filter { it > 0 } // Filtrer les chapitres avec 0 versets si nécessaire
                    ShapeItem(section, heTitle, title, length, chapList, book, heBook)
                }
                // Structure mixte ou inattendue
                else {
                    logger.debug("Structure inattendue pour 'chapters' dans '{}' : {}", title, chapters)
                    ShapeItem(section, heTitle, title, length, emptyList(), book, heBook)
                }
            }
            // Si 'chapters' est un JsonPrimitive avec une valeur entière
            chapters is JsonPrimitive && chapters.jsonPrimitive.intOrNull != null -> {
                val singleChapter = chapters.jsonPrimitive.int
                ShapeItem(section, heTitle, title, length, listOf(singleChapter), book, heBook)
            }
            // Format non supporté
            else -> {
                logger.debug("Format non supporté pour le champ 'chapters' dans '{}' : {}", title, chapters)
                ShapeItem(section, heTitle, title, length, emptyList(), book, heBook)
            }
        }
    }


}
