package com.kdroid.seforim.database.builders.sefaria.book.util

import com.kdroid.seforim.core.model.*
import com.kdroid.seforim.core.model.CommentaryType.*
import com.kdroid.seforim.database.builders.sefaria.book.api.fetchJsonFromApi
import com.kdroid.seforim.database.builders.sefaria.book.model.CommentItem
import com.kdroid.seforim.database.builders.sefaria.book.model.ComplexShapeItem
import com.kdroid.seforim.database.builders.sefaria.book.model.FlexibleShapeItem
import com.kdroid.seforim.database.builders.sefaria.book.model.ShapeItem
import com.kdroid.seforim.database.common.config.json
import com.kdroid.seforim.database.common.constants.BASE_URL
import com.kdroid.seforim.database.common.constants.BLACKLIST
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2


internal val logger = LoggerFactory.getLogger("BookBuilderUtil")


internal suspend fun buildBookFromShape(bookTitle: String, rootFolder: String) {
    val encodedTitle = bookTitle.replace(" ", "%20")
    logger.info("Fetching shape for book: $bookTitle")
    val shapeUrl = "$BASE_URL/shape/$encodedTitle"
    val shapeJson = fetchJsonFromApi(shapeUrl)

    val jsonElement = json.parseToJsonElement(shapeJson)

    //TODO Unify the detection of complex books with that of the Directory builder
    if (jsonElement.jsonArray.firstOrNull()?.jsonObject?.get("isComplex")?.jsonPrimitive?.boolean == true) {
        logger.info("Detected complex book structure for: $bookTitle")
        val complexBook = json.decodeFromString<List<ComplexShapeItem>>(shapeJson).first()
        processComplexBook(complexBook, rootFolder)
    } else {
        logger.info("Detected simple book structure for: $bookTitle")
        val simpleBooks = json.decodeFromString<List<FlexibleShapeItem>>(shapeJson)
        simpleBooks.forEach { shapeItem ->
            val item = shapeItem.toShapeItem(logger)
            BookProcessor().processSimpleBook(item, rootFolder)
        }
    }
}

private suspend fun processComplexBook(complexBook: ComplexShapeItem, rootFolder: String) {
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
            BookProcessor().processSimpleBook(normalShape, rootFolder, title)
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
            quotingCommentary = emptyList(),
            reference = emptyList(),
            otherLinks = emptyList()
        )
    }

    // We retrieve the common list, only with the 'he' field
    val allCommentaries = commentsList
        .filterNot { BLACKLIST.contains(it.index_title) }
        .groupBy { it.collectiveTitle.he ?: "Unknown" }
        .mapNotNull { (commentator, groupedComments) ->
            val heTexts = groupedComments.flatMap { commentItem ->
                listOfNotNull(
                    // Exclure le champ 'text' et ne traiter que 'he'
                    when (val he = commentItem.he) {
                        is JsonArray -> he.joinToString(" ") { it.jsonPrimitive.content }
                        is JsonPrimitive -> he.contentOrNull
                        else -> null
                    }
                ).filter { it.isNotEmpty() }
            }

            if (heTexts.isNotEmpty()) {
                val firstCategory = groupedComments.firstOrNull()?.category ?: ""
                val commentaryType = when (firstCategory.lowercase()) {
                    "commentary" -> COMMENTARY
                    "targum" -> TARGUM
                    "quoting commentary" -> QUOTING_COMMENTARY
                    "reference" -> REFERENCE
                    else -> OTHER_LINKS
                }

                when (commentaryType) {
                    COMMENTARY -> Commentary(
                        commentatorName = commentator,
                        texts = heTexts
                    )
                    TARGUM -> Targum(
                        commentatorName = commentator,
                        texts = heTexts
                    )
                    QUOTING_COMMENTARY -> QuotingCommentary(
                        commentatorName = commentator,
                        texts = heTexts
                    )
                    REFERENCE -> Reference(
                        commentatorName = commentator,
                        texts = heTexts
                    )
                    else -> OtherLinks(
                        commentatorName = commentator,
                        texts = heTexts
                    )
                }
            } else null
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
