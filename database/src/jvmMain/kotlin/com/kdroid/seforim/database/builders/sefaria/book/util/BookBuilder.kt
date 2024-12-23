package com.kdroid.seforim.database.builders.sefaria.book.util

import com.kdroid.seforim.core.model.*
import com.kdroid.seforim.core.model.CommentaryType.*
import com.kdroid.seforim.database.Database
import com.kdroid.seforim.database.builders.sefaria.book.api.fetchJsonFromApi
import com.kdroid.seforim.database.builders.sefaria.book.model.CommentItem
import com.kdroid.seforim.database.builders.sefaria.book.model.ComplexShapeItem
import com.kdroid.seforim.database.builders.sefaria.book.model.FlexibleShapeItem
import com.kdroid.seforim.database.builders.sefaria.book.model.ShapeItem
import com.kdroid.seforim.database.common.config.json
import com.kdroid.seforim.database.common.constants.BASE_URL
import com.kdroid.seforim.database.common.constants.BLACKLIST
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.*
import kotlinx.serialization.protobuf.ProtoBuf
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2


internal val logger = LoggerFactory.getLogger("BookBuilderUtil")


internal suspend fun buildBookFromShape(bookTitle: String, rootFolder: String, database: Database) {
    val encodedTitle = bookTitle.replace(" ", "%20")
    logger.info("Fetching shape for book: $bookTitle")
    val shapeUrl = "$BASE_URL/shape/$encodedTitle"
    val shapeJson = fetchJsonFromApi(shapeUrl)

    val jsonElement = json.parseToJsonElement(shapeJson)

    //TODO Unify the detection of complex books with that of the Directory builder
    if (jsonElement.jsonArray.firstOrNull()?.jsonObject?.get("isComplex")?.jsonPrimitive?.boolean == true) {
        logger.info("Detected complex book structure for: $bookTitle")
        val complexBook = json.decodeFromString<List<ComplexShapeItem>>(shapeJson).first()
        processComplexBook(complexBook, rootFolder, database = database)
    } else {
        logger.info("Detected simple book structure for: $bookTitle")
        val simpleBooks = json.decodeFromString<List<FlexibleShapeItem>>(shapeJson)
        simpleBooks.forEach { shapeItem ->
            val item = shapeItem.toShapeItem(logger)
            BookProcessor().processSimpleBook(item, rootFolder, database = database)
        }
    }
}

private suspend fun processComplexBook(complexBook: ComplexShapeItem, rootFolder: String, database: Database) {
    // For each item in complexBook.chapters, decode to FlexibleShapeItem
    // Then call toShapeItem() to get a standard ShapeItem.
    for (chapterElement in complexBook.chapters) {
        val obj = chapterElement.jsonObject
        val title = obj["title"]?.jsonPrimitive?.content ?: "Unknown"
        logger.info("Processing sub-book: $title")

        try {
            // Decode into FlexibleShapeItem
            val shapeItem = json.decodeFromJsonElement<FlexibleShapeItem>(chapterElement)
            val normalShape = shapeItem.toShapeItem(logger)
            BookProcessor().processSimpleBook(normalShape, rootFolder, title, database = database)
        } catch (e: Exception) {
            logger.info("Failed to process sub-book $title: ${e.message}")
        }
    }
}



private fun Int.toGuemaraInt(): String {
    val numberPart = (this + 1) / 2 + 1
    val suffix = if (this % 2 == 1) "a" else "b"
    return "$numberPart$suffix"
}

internal fun saveVerse(bookTitle: String, chapter: Int, verseNumber: Int, verse: Verse, rootFolder : String) {
    val verseDir = File("$rootFolder/$bookTitle/$chapter")
    if (!verseDir.exists()) verseDir.mkdirs()

    val verseFile = File(verseDir, "$verseNumber.json")
    verseFile.writeText(json.encodeToString(verse))
}

@OptIn(ExperimentalSerializationApi::class)
internal fun saveVerseAsProto(bookTitle: String, chapter: Int, verseNumber: Int, verse: Verse, rootFolder: String) {
    val verseDir = File("$rootFolder/$bookTitle/$chapter")
    if (!verseDir.exists()) verseDir.mkdirs()

    val verseFile = File(verseDir, "$verseNumber.proto")
    val protoData = ProtoBuf.encodeToByteArray(verse)
    verseFile.writeBytes(protoData)
}

internal fun String.removeLastSegment(): String {
    val parts = this.split(":")
    return if (parts.size > 2) {
        parts.dropLast(1).joinToString(":")
    } else {
        this
    }
}

private val categoriesCache = mutableMapOf<String, String>()


internal suspend fun fetchCommentsForVerse(
    bookTitle: String,
    chapter: Int,
    verse: Int,
    shape: ShapeItem,
    isTalmud: Boolean,
): CommentaryResponse {
    val formattedTitle = bookTitle.replace(" ", "%20")
    val commentsUrl = if (shape.length == 1 && shape.title.contains("Introduction")) {
        "$BASE_URL/links/$formattedTitle"
    } else {
        val chapterWithGuemaraCheck = if (isTalmud) chapter.toGuemaraInt() else chapter
        "$BASE_URL/links/$formattedTitle.${chapterWithGuemaraCheck}.$verse"
    }
    logger.debug("Fetching comments for $bookTitle chapter $chapter, verse $verse from: $commentsUrl")

    val commentsJson = fetchJsonFromApi(commentsUrl)

    // Attempt to parse the JSON as a JsonElement
    val parsedJsonElement = try {
        json.parseToJsonElement(commentsJson)
    } catch (e: SerializationException) {
        logger.info("Failed to parse JSON for comments from $commentsUrl: ${e.message}")
        return CommentaryResponse(
            commentary = emptyList(),
            targum = emptyList(),
            quotingCommentary = emptyList(),
            reference = emptyList(),
            otherLinks = emptyList()
        )
    }

    // Check if the response contains an "error" field
    if (parsedJsonElement is JsonObject && parsedJsonElement.containsKey("error")) {
        val errorMsg = parsedJsonElement["error"]?.jsonPrimitive?.content
        logger.info("Skipping comments for verse $chapter:$verse due to error: $errorMsg")
        return CommentaryResponse(
            commentary = emptyList(),
            targum = emptyList(),
            quotingCommentary = emptyList(),
            reference = emptyList(),
            otherLinks = emptyList()
        )
    }

    // Attempt to deserialize to List<CommentItem>
    val commentsList: List<CommentItem> = try {
        json.decodeFromString(commentsJson)
    } catch (e: SerializationException) {
        logger.info("Failed to deserialize comments for verse $chapter:$verse: ${e.message}")
        return CommentaryResponse(
            commentary = emptyList(),
            targum = emptyList(),
            quotingCommentary = emptyList(),
            reference = emptyList(),
            otherLinks = emptyList()
        )
    }

    // Process the comments
    val allCommentaries = commentsList
        .filterNot { BLACKLIST.contains(it.index_title) }
        .groupBy { it.collectiveTitle.he ?: "Unknown" }
        .mapNotNull { (commentatorName, groupedComments) ->
            val path = groupedComments.firstOrNull()?.index_title ?: "Unknown Path"

            // Extraire les textes avec leur référence
            val textsWithRef = groupedComments.mapNotNull { commentItem ->
                val ref = (commentItem.sourceRef).removePrefix(path).trim().removeLastSegment().replace(":", "/") //J'adapte la source à la structure de ma bdd en locale pour pointer directement vers le fichier cible

                val guemeraRef = ref.extractNumberAndLetterAsString()
                val line = ref.removePrefix(guemeraRef ?: "").removePrefix("/").toIntOrNull() ?: 0
                val text = when (val he = commentItem.he) {
                    is JsonArray -> he.joinToString(" ") { it.jsonPrimitive.content }
                    is JsonPrimitive -> he.contentOrNull
                    else -> null
                }
                if (text != null) TextWithRef(text = text, ref = if (guemeraRef != null ) guemeraRef.toGuemaraNumber().toString() + "/" + line else ref) else null
            }.filter { it.text.isNotEmpty() }

            if (textsWithRef.isNotEmpty()) {
                val firstCategory = groupedComments.firstOrNull()?.category?.lowercase() ?: ""
                val commentaryType = when (firstCategory) {
                    "commentary" -> COMMENTARY
                    "targum" -> TARGUM
                    "quoting commentary" -> QUOTING_COMMENTARY
                    "reference" -> REFERENCE
                    else -> OTHER_LINKS
                }


                suspend fun fetchAndFormatCategories(commentatorName: String): String {
                    // Check cache first
                    return categoriesCache.getOrPut(commentatorName) {
                        val url = "$BASE_URL/v2/raw/index/${commentatorName.replace(" ", "%20")}"
                        val jsonString = fetchJsonFromApi(url)
                        try {
                            val response = json.decodeFromString<PathResponse>(jsonString)
                            response.categories.joinToString("/")
                        } catch (e: SerializationException) {
                            logger.error("Failed to parse JSON: ${e.message}", e)
                            ""
                        }
                    }
                }

                val commentator = Commentator(name = commentatorName, path = "${fetchAndFormatCategories(path)}/$path")

                when (commentaryType) {
                    COMMENTARY -> Commentary(
                        commentator = commentator,
                        texts = textsWithRef
                    )
                    TARGUM -> Targum(
                        commentator = commentator,
                        texts = textsWithRef
                    )
                    QUOTING_COMMENTARY -> QuotingCommentary(
                        commentator = commentator,
                        texts = textsWithRef
                    )
                    REFERENCE -> Reference(
                        commentator = commentator,
                        texts = textsWithRef
                    )
                    else -> OtherLinks(
                        commentator = commentator,
                        texts = textsWithRef
                    )
                }
            } else {
                null
            }
        }

    // Distribute by type
    val commentaryList = allCommentaries.filterIsInstance<Commentary>()
    val targumList = allCommentaries.filterIsInstance<Targum>()
    val quotingList = allCommentaries.filterIsInstance<QuotingCommentary>()
    val referenceList = allCommentaries.filterIsInstance<Reference>()
    val otherList = allCommentaries.filterIsInstance<OtherLinks>()

    // Return a structured object
    return CommentaryResponse(
        commentary = commentaryList,
        targum = targumList,
        quotingCommentary = quotingList,
        reference = referenceList,
        otherLinks = otherList
    )
}

fun String.extractNumberAndLetterAsString(): String? {
    // Définir une expression régulière pour capturer un numéro suivi de "a" ou "b"
    val regex = """(\d+)([ab])""".toRegex()
    // Rechercher la première correspondance dans la chaîne
    val matchResult = regex.find(this)
    // Retourner le résultat formaté en String, ou null s'il n'y a pas de correspondance
    return matchResult?.let {
        val number = it.groupValues[1]
        val letter = it.groupValues[2]
        "$number$letter"
    }
}


private fun String.toGuemaraNumber(): Int? {
    // Valider que la chaîne correspond à un format attendu comme "3a" ou "4b"
    val regex = """(\d+)([ab])""".toRegex()
    val matchResult = regex.matchEntire(this) ?: return null

    val numberPart = matchResult.groupValues[1].toInt() // La partie numérique
    val suffix = matchResult.groupValues[2]            // La partie lettre

    return when (suffix) {
        "a" -> (numberPart - 1) * 2 + 1
        "b" -> (numberPart - 1) * 2
        else -> null // En cas de suffixe invalide
    }
}