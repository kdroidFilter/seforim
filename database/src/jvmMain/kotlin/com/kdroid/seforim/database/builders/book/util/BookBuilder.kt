package com.kdroid.seforim.database.builders.book.util

import com.kdroid.seforim.core.model.*
import com.kdroid.seforim.database.builders.book.api.fetchJsonFromApi
import com.kdroid.seforim.database.builders.book.model.*
import com.kdroid.seforim.database.common.config.json
import com.kdroid.seforim.database.common.constants.BASE_URL
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set


/**
 * Fetches commentary data for a specific verse in a chapter of a book.
 * The comments are fetched from a remote API and processed to group them by the commentator's name.
 *
 * @param bookTitle The title of the book for which the comments are fetched. Spaces in the title will be replaced with `%20`.
 * @param chapter The chapter number from which the verse belongs.
 * @param verse The verse number for which the comments are fetched.
 * @return A list of `Commentary` instances containing the commentator's name and their corresponding texts.
 */
internal suspend fun fetchCommentsForVerse(
    bookTitle: String,
    chapter: Int,
    verse: Int,
    shape: ShapeItem
): CommentaryResponse {
    val formattedTitle = bookTitle.replace(" ", "%20")
    val commentsUrl = if (shape.length == 1 && shape.title.contains("Introduction")) {
        "$BASE_URL/links/$formattedTitle"
    } else {
        "$BASE_URL/links/$formattedTitle.$chapter.$verse"
    }
    logger.debug("Fetching comments for $bookTitle chapter $chapter, verse $verse from: $commentsUrl")

    val commentsJson = fetchJsonFromApi(commentsUrl)

    // Tenter de parser le JSON en tant que JsonElement
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

    // Vérifier si la réponse contient un champ "error"
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

    // Tenter de désérialiser en List<CommentItem>
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

    // On récupère la liste commune, uniquement avec le champ 'he'
    val allCommentaries = commentsList
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
                    "commentary" -> "COMMENTARY"
                    "quoting commentary" -> "QUOTING_COMMENTARY"
                    "reference" -> "REFERENCE"
                    else -> "AUTRE"
                }

                when (commentaryType) {
                    "COMMENTARY" -> Commentary(
                        commentatorName = commentator,
                        texts = heTexts
                    )
                    "QUOTING_COMMENTARY" -> QuotingCommentary(
                        commentatorName = commentator,
                        texts = heTexts
                    )
                    "REFERENCE" -> Reference(
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

    // Répartir par type
    val commentaryList = allCommentaries.filterIsInstance<Commentary>()
    val quotingList = allCommentaries.filterIsInstance<QuotingCommentary>()
    val referenceList = allCommentaries.filterIsInstance<Reference>()
    val otherList = allCommentaries.filterIsInstance<OtherLinks>()

    // Retourner un objet structuré
    return CommentaryResponse(
        commentary = commentaryList,
        quotingCommentary = quotingList,
        reference = referenceList,
        otherLinks = otherList
    )
}


internal suspend fun buildBookFromShape(bookTitle: String, rootFolder: String) {
    val encodedTitle = bookTitle.replace(" ", "%20")
    logger.info("Fetching shape for book: $bookTitle")
    val shapeUrl = "$BASE_URL/shape/$encodedTitle"
    val shapeJson = fetchJsonFromApi(shapeUrl)

    val jsonElement = json.parseToJsonElement(shapeJson)

    if (jsonElement.jsonArray.firstOrNull()?.jsonObject?.get("isComplex")?.jsonPrimitive?.boolean == true) {
        logger.info("Detected complex book structure for: $bookTitle")
        val complexBook = json.decodeFromString<List<ComplexShapeItem>>(shapeJson).first()
        processComplexBook(complexBook, rootFolder)
    } else {
        logger.info("Detected simple book structure for: $bookTitle")
        val simpleBooks = json.decodeFromString<List<FlexibleShapeItem>>(shapeJson)
        simpleBooks.forEach { shapeItem ->
            val item = shapeItem.toShapeItem(logger)
            processSimpleBook(item, rootFolder)
        }
    }
}

internal suspend fun processComplexBook(complexBook: ComplexShapeItem, rootFolder: String) {
    // Pour chaque élément dans complexBook.chapters, décoder en FlexibleShapeItem
    // Puis appeler toShapeItem() pour obtenir un ShapeItem standard.
    for (chapterElement in complexBook.chapters) {
        val obj = chapterElement.jsonObject
        val title = obj["title"]?.jsonPrimitive?.content ?: "Unknown"
        logger.info("Processing sub-book: $title")

        try {
            // Décoder en FlexibleShapeItem
            val shapeItem = json.decodeFromJsonElement<FlexibleShapeItem>(chapterElement)
            val normalShape = shapeItem.toShapeItem(logger)
            processSimpleBook(normalShape, rootFolder)
        } catch (e: Exception) {
            logger.info("Failed to process sub-book $title: ${e.message}")
            // Vous pouvez choisir de continuer ou de stopper le processus selon le besoin
        }
    }
}


internal suspend fun processSimpleBook(shape: ShapeItem, rootFolder: String) {
    val totalVerses = shape.chapters.sum()
    var processedVerses = 0
    val bookTitle = shape.title

    logger.info("Processing book: ${shape.title}")

    val chapterCommentators = mutableMapOf<Int, MutableSet<String>>()

    shape.chapters.forEachIndexed { chapterIndex, nbVerses ->
        chapterCommentators[chapterIndex] = mutableSetOf()
        logger.info("Processing chapter ${chapterIndex + 1} with $nbVerses verses")

        if (shape.length == 1 && shape.title.contains("Introduction")) {
            // Cas spécial : length == 1, fetcher le texte sans ajouter "1.1"
            val endpointTitle = shape.title.replace(" ", "%20")
            val textUrl = "$BASE_URL/v3/texts/$endpointTitle"
            val verseJson = fetchJsonFromApi(textUrl)

            // Vérification des erreurs
            val parsedJson = try {
                json.parseToJsonElement(verseJson).jsonObject
            } catch (e: SerializationException) {
                logger.info("Failed to parse JSON for book ${shape.title}: ${e.message}")
                return@forEachIndexed
            }

            if (parsedJson.containsKey("error")) {
                val errorMsg = parsedJson["error"]?.jsonPrimitive?.content
                logger.info("Skipping book ${shape.title} due to error: $errorMsg")
                return@forEachIndexed
            }

            // Récupération des versets directement depuis le tableau "text"
            val textArray = parsedJson["versions"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("text")?.jsonArray

            if (textArray != null) {
                textArray.forEachIndexed { verseIndex, verseContent ->
                    val verseText = when (verseContent) {
                        is JsonPrimitive -> verseContent.content
                        is JsonArray -> verseContent.jsonArray.joinToString(" ") { it.jsonPrimitive.content }
                        else -> ""
                    }

                    val comments = fetchCommentsForVerse(endpointTitle, chapterIndex + 1, verseIndex + 1, shape = shape)

                    comments.commentary.forEach { commentary ->
                        chapterCommentators[chapterIndex]?.add(commentary.commentatorName)
                    }

                    val verse = Verse(
                        number = verseIndex + 1,
                        text = verseText,
                        commentary = comments.commentary,
                        quotingCommentary = comments.quotingCommentary,
                        reference = comments.reference,
                        otherLinks = comments.otherLinks
                    )

                    saveVerse(bookTitle, chapterIndex + 1, verseIndex + 1, verse, rootFolder)
                    processedVerses++
                    logger.info("Processed verse ${chapterIndex + 1}:${verseIndex + 1}")
                }
            } else {
                logger.info("No text found for book ${shape.title}")
            }
        } else {
            // Cas standard : plusieurs chapitres et versets
            if (nbVerses == 0) {
                logger.info("Chapter ${chapterIndex + 1} has 0 verses, skipping.")
                return@forEachIndexed
            }

            (1..nbVerses).forEach { verseNumber ->
                val endpointTitle = shape.title.replace(" ", "%20")
                val textUrl = "$BASE_URL/v3/texts/$endpointTitle ${chapterIndex + 1}.$verseNumber"
                val verseJson = fetchJsonFromApi(textUrl)

                // Vérification des erreurs
                val parsedJson = try {
                    json.parseToJsonElement(verseJson)
                } catch (e: SerializationException) {
                    logger.info("Failed to parse JSON for verse ${chapterIndex + 1}:$verseNumber: ${e.message}")
                    return@forEach
                }

                if (parsedJson is JsonObject && parsedJson.containsKey("error")) {
                    val errorMsg = parsedJson["error"]?.jsonPrimitive?.content
                    logger.info("Skipping verse ${chapterIndex + 1}:$verseNumber due to error: $errorMsg")
                    return@forEach
                }

                val verseResponse = try {
                    json.decodeFromString<VerseResponse>(verseJson)
                } catch (e: SerializationException) {
                    logger.info("Failed to deserialize VerseResponse for verse ${chapterIndex + 1}:$verseNumber: ${e.message}")
                    return@forEach
                }

                val verseText = when (val textElement = verseResponse.versions.firstOrNull()?.text) {
                    is JsonPrimitive -> textElement.content
                    is JsonArray -> textElement.jsonArray.joinToString(" ") { it.jsonPrimitive.content }
                    else -> ""
                }

                val comments = fetchCommentsForVerse(endpointTitle, chapterIndex + 1, verseNumber, shape = shape)

                comments.commentary.forEach { commentary ->
                    chapterCommentators[chapterIndex]?.add(commentary.commentatorName)
                }

                val verse = Verse(
                    number = verseNumber,
                    text = verseText,
                    commentary = comments.commentary,
                    quotingCommentary = comments.quotingCommentary,
                    reference = comments.reference,
                    otherLinks = comments.otherLinks
                )

                saveVerse(bookTitle, chapterIndex + 1, verseNumber, verse, rootFolder)
                processedVerses++
                val progress = (processedVerses.toDouble() / totalVerses * 100).toInt()
                logger.info("Progress: $progress% ($processedVerses/$totalVerses verses)")
            }
        }
    }

    //Creer l'index du livre
    val chaptersIndexList = shape.chapters.mapIndexed { chapterIndex, nbVerses ->
        ChapterIndex(
            chapterNumber = chapterIndex + 1,
            numberOfVerses = nbVerses,
            commentators = chapterCommentators[chapterIndex]?.toList() ?: emptyList()
        )
    }

    val bookIndex = BookIndex(
        title = shape.title,
        heTitle = shape.heBook,
        numberOfChapters = shape.chapters.size,
        chapters = chaptersIndexList
    )

    val indexFile = File("$rootFolder/$bookTitle/index.json")
    if (!indexFile.parentFile.exists()) indexFile.parentFile.mkdirs()
    indexFile.writeText(json.encodeToString(bookIndex))

    logger.info("Index file created for $bookTitle: ${indexFile.absolutePath}")
}

private fun saveVerse(bookTitle: String, chapter: Int, verseNumber: Int, verse: Verse, rootFolder : String) {
    val verseDir = File("$rootFolder/$bookTitle/$chapter")
    if (!verseDir.exists()) verseDir.mkdirs()

    val verseFile = File(verseDir, "$verseNumber.json")
    verseFile.writeText(json.encodeToString(verse))
}
