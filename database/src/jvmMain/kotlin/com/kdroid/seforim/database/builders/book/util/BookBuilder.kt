package com.kdroid.seforim.database.builders.book.util

import com.kdroid.seforim.core.model.*
import com.kdroid.seforim.database.builders.book.api.fetchJsonFromApi
import com.kdroid.seforim.database.common.config.json
import com.kdroid.seforim.database.builders.book.model.CommentItem
import com.kdroid.seforim.database.builders.book.model.ShapeItem
import com.kdroid.seforim.database.builders.book.model.VerseResponse
import com.kdroid.seforim.database.common.constants.BASE_URL
import com.kdroid.seforim.database.common.constants.GENERATED_FOLDER
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import kotlin.collections.List
import kotlin.collections.MutableSet
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.emptyList
import kotlin.collections.filter
import kotlin.collections.firstOrNull
import kotlin.collections.flatMap
import kotlin.collections.forEach
import kotlin.collections.forEachIndexed
import kotlin.collections.groupBy
import kotlin.collections.isNotEmpty
import kotlin.collections.joinToString
import kotlin.collections.listOfNotNull
import kotlin.collections.mapIndexed
import kotlin.collections.mapNotNull
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.collections.set
import kotlin.collections.sum
import kotlin.collections.toList



/**
 * Fetches commentary data for a specific verse in a chapter of a book.
 * The comments are fetched from a remote API and processed to group them by the commentator's name.
 *
 * @param bookTitle The title of the book for which the comments are fetched. Spaces in the title will be replaced with `%20`.
 * @param chapter The chapter number from which the verse belongs.
 * @param verse The verse number for which the comments are fetched.
 * @return A list of `Commentary` instances containing the commentator's name and their corresponding texts.
 */
internal suspend fun fetchCommentsForVerse(bookTitle: String, chapter: Int, verse: Int): CommentaryResponse {
    val formattedTitle = bookTitle.replace(" ", "%20")
    val commentsUrl = "$BASE_URL/links/$formattedTitle.$chapter.$verse"
    logger.debug("Fetching comments for $bookTitle chapter $chapter, verse $verse from: $commentsUrl")

    val commentsJson = fetchJsonFromApi(commentsUrl)
    val commentsList = json.decodeFromString<List<CommentItem>>(commentsJson)

    // On récupère la liste commune
    val allCommentaries = commentsList
        .groupBy { it.collectiveTitle.he ?: "Unknown" }
        .mapNotNull { (commentator, groupedComments) ->
            val texts = groupedComments.flatMap { commentItem ->
                listOfNotNull(
                    when (commentItem.text) {
                        else -> null
                    },
                    when (commentItem.he) {
                        is JsonArray -> commentItem.he.joinToString(" ") { it.toString() }
                        is JsonPrimitive -> commentItem.he.contentOrNull
                        else -> null
                    }
                ).filter { it.isNotEmpty() }
            }

            if (texts.isNotEmpty()) {
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
                        texts = texts
                    )
                    "QUOTING_COMMENTARY" -> QuotingCommentary(
                        commentatorName = commentator,
                        texts = texts
                    )
                    "REFERENCE" -> Reference(
                        commentatorName = commentator,
                        texts = texts
                    )
                    else -> OtherLinks(
                        commentatorName = commentator,
                        texts = texts
                    )
                }
            } else null
        }

    // Maintenant on répartit par type :
    val commentaryList = allCommentaries.filterIsInstance<Commentary>()
    val quotingList = allCommentaries.filterIsInstance<QuotingCommentary>()
    val referenceList = allCommentaries.filterIsInstance<Reference>()
    val otherList = allCommentaries.filterIsInstance<OtherLinks>()

    // On retourne un objet structuré
    return CommentaryResponse(
        commentary = commentaryList,
        quotingCommentary = quotingList,
        reference = referenceList,
        otherLinks = otherList
    )
}

/**
 * Builds and organizes the hierarchical structure of a book from its associated shape data,
 * processing chapters, verses, and their related commentaries. Saves the results into a
 * file structure and generates an index file for the book.
 *
 * @param bookTitle The title of the book to be built and processed.
 */
internal suspend fun buildBookFromShape(bookTitle: String) {
    val encodedTitle = bookTitle.replace(" ", "%20")
    logger.info("Building book from shape for title: $bookTitle")
    val shapeUrl = "$BASE_URL/shape/${encodedTitle}"
    logger.debug("Fetching shape from: $shapeUrl")
    val shapeJson = fetchJsonFromApi(shapeUrl)

    logger.debug("Decoding shape JSON for $bookTitle")
    val shapeList = json.decodeFromString<List<ShapeItem>>(shapeJson)
    val shape = shapeList.firstOrNull { it.title == bookTitle }
        ?: throw IllegalStateException("No shape found for the book $bookTitle")

    logger.info("Shape found for $bookTitle: ${shape.book}")

    val totalVerses = shape.chapters.sum()
    var processedVerses = 0

    // Preparing the structure for the index
    // For each chapter, we will store:
    // - a set of commentators (to avoid duplicates)
    val chapterCommentators = mutableMapOf<Int, MutableSet<String>>()
    shape.chapters.forEachIndexed { chapterIndex, nbVerses ->
        chapterCommentators[chapterIndex] = mutableSetOf()  // Initialize the set

        logger.info("Processing chapter ${chapterIndex + 1} with $nbVerses verses")
        (1..nbVerses).forEach { verseNumber ->
            val endpointTitle = shape.title.replace(" ", "%20")
            val textUrl = "$BASE_URL/v3/texts/$endpointTitle ${chapterIndex + 1}.$verseNumber"
            val verseJson = fetchJsonFromApi(textUrl)
            val verseResponse = json.decodeFromString<VerseResponse>(verseJson)

            val verseText = verseResponse.versions.firstOrNull()?.text ?: ""
            logger.debug("Verse ${chapterIndex + 1}:$verseNumber text: ${verseText.take(50)}...")

            // Adding comments
            val comments = fetchCommentsForVerse(shape.title.replace(" ", "%20"), chapterIndex + 1, verseNumber)

            // Add the names of commentators found in this verse to the chapter's set
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

            // Save each verse in a separate file
            val verseDir = File("$GENERATED_FOLDER/${bookTitle}/${chapterIndex + 1}")
            if (!verseDir.exists()) verseDir.mkdirs()

            val verseFile = File(verseDir, "$verseNumber.json")
            verseFile.writeText(json.encodeToString(verse))

            // Update progress
            processedVerses++
            val progress = (processedVerses.toDouble() / totalVerses * 100).toInt()
            logger.info("Progress: $progress% ($processedVerses/$totalVerses verses processed)")
        }
    }

    logger.info("Book $bookTitle has been saved hierarchically by chapters and verses.")

    // Once finished, build the global index
    val chaptersIndexList = shape.chapters.mapIndexed { chapterIndex, nbVerses ->
        ChapterIndex(
            chapterNumber = chapterIndex + 1,
            numberOfVerses = nbVerses,
            commentators = chapterCommentators[chapterIndex]?.toList() ?: emptyList()
        )
    }

    val bookIndex = BookIndex(
        title = bookTitle,
        heTitle = shape.heBook,
        numberOfChapters = shape.chapters.size,
        chapters = chaptersIndexList
    )

    // Write the index to an index.json file
    val indexFile = File("generated/${bookTitle}/index.json")
    if (!indexFile.parentFile.exists()) indexFile.parentFile.mkdirs()
    indexFile.writeText(json.encodeToString(bookIndex))

    logger.info("Index file for $bookTitle has been created: ${indexFile.absolutePath}")
}



