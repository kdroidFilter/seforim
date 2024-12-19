package com.kdroid.seforim.database.builders.sefaria.book.util

import com.kdroid.seforim.core.model.*
import com.kdroid.seforim.database.builders.sefaria.book.api.fetchJsonFromApi
import com.kdroid.seforim.database.builders.sefaria.book.model.*
import com.kdroid.seforim.database.common.config.json
import com.kdroid.seforim.database.common.constants.BASE_URL
import com.kdroid.seforim.database.common.constants.BLACKLIST
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set


internal val logger = LoggerFactory.getLogger("BookBuilderUtil")


/**
 * Builds a book structure based on the specified title and saves it in the specified root folder.
 * This function retrieves book data through an API call, processes its structure,
 * and handles both complex and simple book structures accordingly.
 *
 * @param bookTitle The title of the book to be processed. Spaces in the title will be encoded to "%20".
 * @param rootFolder The root folder path where the book data will be processed and saved.
 */
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
            processSimpleBook(item, rootFolder)
        }
    }
}

/**
 * Processes a `ComplexShapeItem` by iterating through its chapters, decoding them into `FlexibleShapeItem`,
 * converting them into `ShapeItem`, and subsequently processing them as simple books.
 *
 * @param complexBook The `ComplexShapeItem` instance containing details about the book, its chapters,
 *                    and metadata to be processed.
 * @param rootFolder  The root folder path where processed outputs or related data may be stored.
 */
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
            processSimpleBook(normalShape, rootFolder)
        } catch (e: Exception) {
            logger.info("Failed to process sub-book $title: ${e.message}")
        }
    }
}

/**
 * Processes a book defined by the given shape and stores its data in the specified folder.
 *
 * The method retrieves and processes chapters and verses of the book. It handles special cases such as
 * single-chapter books or books marked as "Introduction". This involves fetching the text and associated
 * commentary for each verse using APIs and then saving the processed data to the given root folder.
 * An index file containing metadata about the book is also created at the end of the process.
 *
 * @param shape Represents the metadata of the book being processed, including title, chapters, and other details.
 * @param rootFolder The root directory where the processed book data and the index file will be stored.
 */
private suspend fun processSimpleBook(shape: ShapeItem, rootFolder: String) {
    val totalVerses = shape.chapters.sum()
    var processedVerses = 0
    val bookTitle = shape.title
    val isTalmud = isTalmud(shape.book)

    logger.info("Processing book: ${shape.title}")

    val chapterCommentators = mutableMapOf<Int, MutableSet<String>>()


    // Define a coroutine scope
    coroutineScope {
        // Initialize a Semaphore with 50 permits
        val semaphore = Semaphore(50)

        // List to store all tasks
        val tasks = mutableListOf<Deferred<Unit>>()

        shape.chapters.forEachIndexed { chapterIndex, nbVerses ->
            val chapterNumber = if (isTalmud) chapterIndex - 1 else chapterIndex + 1
            chapterCommentators[chapterIndex] = mutableSetOf()
            logger.info("Processing chapter $chapterNumber with $nbVerses verses")

            if (shape.length == 1 && shape.title.contains("Introduction")) {
                // Special case: length == 1, fetch text without adding "1.1"
                val endpointTitle = shape.title.replace(" ", "%20")
                val textUrl = "$BASE_URL/v3/texts/$endpointTitle"
                val verseJson = fetchJsonFromApi(textUrl)

                // Error checking
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

                // Retrieve verses directly from the "text" array
                val textArray = parsedJson["versions"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("text")?.jsonArray

                if (textArray != null) {
                    textArray.forEachIndexed { verseIndex, verseContent ->
                        // Add a task for each verse
                        val task = async {
                            semaphore.withPermit {
                                val verseText = when (verseContent) {
                                    is JsonPrimitive -> verseContent.content
                                    is JsonArray -> verseContent.jsonArray.joinToString(" ") { it.jsonPrimitive.content }
                                    else -> ""
                                }

                                val comments = fetchCommentsForVerse(endpointTitle, chapterNumber, verseIndex + 1, shape = shape, isTalmud = isTalmud)

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

                                saveVerse(bookTitle, chapterNumber, verseIndex + 1, verse, rootFolder)
                                processedVerses++
                                logger.info("Processed verse $chapterNumber:${verseIndex + 1}")
                            }
                        }
                        tasks.add(task)
                    }
                } else {
                    logger.info("No text found for book ${shape.title}")
                }
            } else {
                // Standard case: multiple chapters and verses
                if (nbVerses == 0) {
                    logger.info("Chapter $chapterNumber has 0 verses, skipping.")
                    return@forEachIndexed
                }

                (1..nbVerses).forEach { verseNumber ->
                    // Add a task for each verse
                    val task = async {
                        semaphore.withPermit {
                            val endpointTitle = shape.title
                            val chapterWithGuemaraCheck = if (isTalmud) chapterNumber.toGuemaraInt() else chapterNumber
                            val textUrl = "$BASE_URL/v3/texts/$endpointTitle ${chapterWithGuemaraCheck}.$verseNumber"
                            val verseJson = fetchJsonFromApi(textUrl.replace(" ", "%20"))

                            // Error checking
                            val parsedJson = try {
                                json.parseToJsonElement(verseJson)
                            } catch (e: SerializationException) {
                                logger.info("Failed to parse JSON for verse $chapterNumber:$verseNumber: ${e.message}")
                                return@withPermit
                            }

                            if (parsedJson is JsonObject && parsedJson.containsKey("error")) {
                                val errorMsg = parsedJson["error"]?.jsonPrimitive?.content
                                logger.info("Skipping verse $chapterNumber:$verseNumber due to error: $errorMsg")
                                return@withPermit
                            }

                            val verseResponse = try {
                                json.decodeFromString<VerseResponse>(verseJson)
                            } catch (e: SerializationException) {
                                logger.info("Failed to deserialize VerseResponse for verse $chapterNumber:$verseNumber: ${e.message}")
                                return@withPermit
                            }

                            val verseText = when (val textElement = verseResponse.versions.firstOrNull()?.text) {
                                is JsonPrimitive -> textElement.content
                                is JsonArray -> textElement.jsonArray.joinToString(" ") { it.jsonPrimitive.content }
                                else -> ""
                            }

                            val comments = fetchCommentsForVerse(endpointTitle, chapterNumber, verseNumber, shape = shape, isTalmud = isTalmud)

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

                            saveVerse(bookTitle, chapterNumber, verseNumber, verse, rootFolder)
                            processedVerses++
                            val progress = (processedVerses.toDouble() / totalVerses * 100).toInt()
                            logger.info("Progress: $progress% ($processedVerses/$totalVerses verses)")
                        }
                    }
                    tasks.add(task)
                }
            }
        }

        // Wait for all tasks to complete
        tasks.awaitAll()

        // Create the book index
        val chaptersIndexList = shape.chapters.mapIndexed { chapterIndex, nbVerses ->
            ChapterIndex(
                chapterNumber = if (isTalmud) chapterIndex - 1 else chapterIndex + 1,
                numberOfVerses = nbVerses,
                commentators = chapterCommentators[chapterIndex]?.toList() ?: emptyList()
            )
        }

        val bookIndex = BookIndex(
            type = if (isTalmud) BookType.TALMUD else BookType.OTHER,
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
}

private fun Int.toGuemaraInt(): String {
    val numberPart = (this + 1) / 2 + 1
    val suffix = if (this % 2 == 1) "a" else "b"
    return "$numberPart$suffix"
}

private suspend fun isTalmud(bookTitle: String): Boolean {
    logger.info("Checking if '$bookTitle' contains 'Talmud' in addressTypes")
    val jsonResponse = fetchJsonFromApi("$BASE_URL/v2/raw/index/$bookTitle")
    return try {
        val jsonElement = Json.parseToJsonElement(jsonResponse)
        val addressTypes = jsonElement.jsonObject["schema"]?.jsonObject?.get("addressTypes")

        if (addressTypes != null && addressTypes is JsonArray) {
            val containsTalmud = addressTypes.any { it.jsonPrimitive.content == "Talmud" }
            logger.info("Result for '$bookTitle': contains 'Talmud' = $containsTalmud")
            containsTalmud
        } else {
            logger.warn("'addressTypes' is missing or not an array in the response for '$bookTitle'")
            false
        }
    } catch (e: Exception) {
        logger.error("Error parsing JSON response for '$bookTitle': ${e.message}", e)
        false
    }
}



/**
 * Saves a verse to a JSON file in a directory structure based on the book title and chapter number.
 *
 * @param bookTitle The title of the book to which the verse belongs.
 * @param chapter The chapter number in which the verse is found.
 * @param verseNumber The number of the verse within the chapter.
 * @param verse The verse object containing the text and associated metadata.
 * @param rootFolder The root folder where the book's data directory is located.
 */
private fun saveVerse(bookTitle: String, chapter: Int, verseNumber: Int, verse: Verse, rootFolder : String) {
    val verseDir = File("$rootFolder/$bookTitle/$chapter")
    if (!verseDir.exists()) verseDir.mkdirs()

    val verseFile = File(verseDir, "$verseNumber.json")
    verseFile.writeText(json.encodeToString(verse))
}

/**
 * Fetches and formats comments related to a specific verse from an external data source.
 * This method builds a formatted URL based on the input parameters, fetches JSON data from the endpoint,
 * and processes it to organize the comments into structured categories like Commentary, Quoting Commentary,
 * Reference, and Other Links.
 *
 * If the JSON response cannot be parsed or contains an error field, it returns an empty `CommentaryResponse` object.
 *
 * @param bookTitle The title of the book (e.g., "Genesis"). Spaces in the title are URL-encoded.
 * @param chapter The chapter number of the requested verse.
 * @param verse The verse number in the requested chapter.
 * @param shape The shape object containing metadata about the book and its structure.
 *
 * @return A `CommentaryResponse` object containing categorized commentaries for the specified verse.
 */
private suspend fun fetchCommentsForVerse(
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
        .filterNot { BLACKLIST.contains(it.collectiveTitle.en ?: "Unknown") }
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
                    else -> "OTHER_LINKS"
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

    // Distribute by type
    val commentaryList = allCommentaries.filterIsInstance<Commentary>()
    val quotingList = allCommentaries.filterIsInstance<QuotingCommentary>()
    val referenceList = allCommentaries.filterIsInstance<Reference>()
    val otherList = allCommentaries.filterIsInstance<OtherLinks>()

    // Return a structured object
    return CommentaryResponse(
        commentary = commentaryList,
        quotingCommentary = quotingList,
        reference = referenceList,
        otherLinks = otherList
    )
}
