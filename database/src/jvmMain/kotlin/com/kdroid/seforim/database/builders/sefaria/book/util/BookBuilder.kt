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
import com.kdroid.seforim.utils.extractNumberAndLetterAsString
import com.kdroid.seforim.utils.logToFile
import com.kdroid.seforim.utils.normalizeRef
import com.kdroid.seforim.utils.toGuemaraNumber
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

internal fun saveVerse(bookTitle: String, chapter: Int, verseNumber: Int, verse: Verse, rootFolder: String) {
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

    val parsedJsonElement = try {
        json.parseToJsonElement(commentsJson)
    } catch (e: SerializationException) {
        logger.info("Failed to parse JSON for comments from $commentsUrl: ${e.message}")
        return CommentaryResponse.empty()
    }

    if (parsedJsonElement is JsonObject && parsedJsonElement.containsKey("error")) {
        val errorMsg = parsedJsonElement["error"]?.jsonPrimitive?.content
        logger.info("Skipping comments for verse $chapter:$verse due to error: $errorMsg")
        return CommentaryResponse.empty()
    }

    val commentsList: List<CommentItem> = try {
        json.decodeFromString(commentsJson)
    } catch (e: SerializationException) {
        logger.info("Failed to deserialize comments for verse $chapter:$verse: ${e.message}")
        return CommentaryResponse.empty()
    }

    val allCommentaries = processComments(commentsList)

    return CommentaryResponse(
        commentary = allCommentaries.filterIsInstance<Commentary>(),
        targum = allCommentaries.filterIsInstance<Targum>(),
        quotingCommentary = allCommentaries.filterIsInstance<QuotingCommentary>(),
        source = allCommentaries.filterIsInstance<Source>(),
        otherLinks = allCommentaries.filterIsInstance<OtherLinks>()
    )
}

internal fun processComments(commentsList: List<CommentItem>): List<Any> {
    val debugLog = StringBuilder()
    debugLog.appendLine("Processing Comments")
    debugLog.appendLine("--------------------")

    val result = commentsList
        // Filtrer ce qui est dans la blacklist
        .filterNot { BLACKLIST.contains(it.index_title) }
        // Grouper par le nom du commentateur (exemple)
        .groupBy { it.collectiveTitle.he ?: "Unknown" }
        .mapNotNull { (commentatorName, groupedComments) ->
            // Récupérer le premier index_title pour représenter ce groupe
            val indexTitle = groupedComments.firstOrNull()?.index_title ?: "Unknown Path"

            // Extraire nos textes + références
            val textsWithRef = groupedComments.mapNotNull { commentItem ->
                val refCleaned = if (commentItem.sourceRef.contains(indexTitle)) {
                    // On retire le préfixe "Rashi on Horayot" etc.
                    commentItem.sourceRef.removePrefix(indexTitle).trim()
                } else {
                    commentItem.sourceRef.trim()
                }

                // Normaliser la référence
                val ref = refCleaned.normalizeRef()

                // Séparer les segments après normalisation
                val segments = ref.split(":")

                // Assurer qu'il y a au moins deux segments
                if (segments.size < 2) {
                    logToFile("ref.txt", "invalid reference for index : $indexTitle with ref : $ref")
                    return@mapNotNull null
                }

                val firstSegment = segments[0]
                val secondSegmentRaw = segments[1]

                // Gérer les plages de versets, ex. "36-42" => "36"
                val secondSegment = secondSegmentRaw.split("-")[0]

                // Extraction possible de la page Guemara ex. "2a"
                val guemeraRef = firstSegment.extractNumberAndLetterAsString()

                // Calcul du chapitre
                val chapterComment = guemeraRef?.toGuemaraNumber()
                    ?: firstSegment.toIntOrNull()
                    ?: 0

                // Calcul du verset
                val verseNumber = secondSegment.toIntOrNull() ?: 1

                // On imagine qu’on veut concaténer tous les éléments textuels
                val text = when (val he = commentItem.he) {
                    is JsonArray -> he.joinToString(" ") { it.jsonPrimitive.content }
                    is JsonPrimitive -> he.contentOrNull
                    else -> null
                }

                // Validation supplémentaire si nécessaire
                if (secondSegment.toIntOrNull() == null) {
                    logToFile("ref.txt", "error for index : $indexTitle with ref : $ref, verse segment : $secondSegment")
                }

                debugLog.appendLine()
                debugLog.appendLine(" sourceRef: ${commentItem.sourceRef}")
                debugLog.appendLine("Output chapter : $chapterComment")
                debugLog.appendLine("Output verse : $verseNumber")

                text?.let {
                    TextWithRef(
                        text = text,
                        reference = Reference(chapter = chapterComment, verse = verseNumber, hebrewRef = commentItem.sourceHeRef )
                    )
                }
            }.filter { it.text.isNotEmpty() }

            // Si on a extrait quelque chose
            if (textsWithRef.isNotEmpty()) {
                val firstCategory = groupedComments.firstOrNull()?.category?.lowercase() ?: ""
                val commentaryType = when (firstCategory) {
                    "commentary" -> COMMENTARY
                    "targum" -> TARGUM
                    "quoting commentary" -> QUOTING_COMMENTARY
                    "reference" -> REFERENCE
                    else -> OTHER_LINKS
                }

                val commentator = Commentator(name = commentatorName, bookId = indexTitle)

                // Retourner un objet typé selon la catégorie
                when (commentaryType) {
                    COMMENTARY -> Commentary(commentator = commentator, texts = textsWithRef)
                    TARGUM -> Targum(commentator = commentator, texts = textsWithRef)
                    QUOTING_COMMENTARY -> QuotingCommentary(commentator = commentator, texts = textsWithRef)
                    REFERENCE -> Source(commentator = commentator, texts = textsWithRef)
                    else -> OtherLinks(commentator = commentator, texts = textsWithRef)
                }
            } else {
                null
            }
        }

    debugLog.appendLine("--------------------")
    logToFile("debug_log.txt", debugLog.toString())

    return result
}

private fun CommentaryResponse.Companion.empty() = CommentaryResponse(
    commentary = emptyList(),
    targum = emptyList(),
    quotingCommentary = emptyList(),
    source = emptyList(),
    otherLinks = emptyList()
)



