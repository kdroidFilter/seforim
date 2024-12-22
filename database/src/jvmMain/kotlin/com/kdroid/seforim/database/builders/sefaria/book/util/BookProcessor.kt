package com.kdroid.seforim.database.builders.sefaria.book.util

import com.kdroid.seforim.core.model.*
import com.kdroid.seforim.database.builders.sefaria.book.api.fetchJsonFromApi
import com.kdroid.seforim.database.builders.sefaria.book.model.ShapeItem
import com.kdroid.seforim.database.builders.sefaria.book.model.VerseResponse
import com.kdroid.seforim.database.common.config.json
import com.kdroid.seforim.database.common.constants.BASE_URL
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File

/**
 * Classe responsable du traitement des livres en récupérant les versets, les commentaires,
 * en sauvegardant les données et en créant des index.
 */
class BookProcessor {

    /**
     * Orchestration principale pour traiter un livre simple.
     *
     * @param shape L'objet ShapeItem représentant la structure du livre.
     * @param rootFolder Le répertoire racine où les données seront sauvegardées.
     */
    internal suspend fun processSimpleBook(shape: ShapeItem, rootFolder: String, subBookTitle: String? = null) {
        val totalVerses = shape.chapters.sum()
        var processedVerses = 0
        val bookTitle = shape.title
        val isTalmud = isTalmud(shape.book)

        logger.info("Processing book: $bookTitle")

        val chapterCommentators = mutableMapOf<Int, MutableSet<String>>()

        coroutineScope {
            val semaphore = Semaphore(20)
            val tasks = mutableListOf<Deferred<Unit>>()

            shape.chapters.forEachIndexed { chapterIndex, nbVerses ->
                val chapterNumber = chapterIndex + 1
                chapterCommentators[chapterIndex] = mutableSetOf()
                logger.info("Processing chapter $chapterNumber with $nbVerses verses")

                if (isIntroductionChapter(shape)) {
                    val task = async {
                        val verses = fetchIntroductionVerses(shape)
                        val isTalmud = isTalmud(shape.book)
                        verses.forEachIndexed { verseIndex, verseContent ->
                            semaphore.withPermit {
                                processIntroductionVerse(
                                    shape.title,
                                    chapterNumber,
                                    verseIndex + 1,
                                    verseContent,
                                    shape,
                                    chapterCommentators,
                                    rootFolder,
                                    isTalmud
                                )
                                synchronized(this@BookProcessor) { processedVerses++ }
                                logger.info("Processed verse $chapterNumber:${verseIndex + 1}")
                            }
                        }
                    }
                    tasks.add(task)
                } else {
                    // Chapitre standard
                    if (nbVerses == 0) {
                        logger.info("Chapter $chapterNumber has 0 verses, skipping.")
                        return@forEachIndexed
                    }

                    for (verseNumber in 1..nbVerses) {
                        val task = async {
                            semaphore.withPermit {
                                processStandardVerse(
                                    shape,
                                    bookTitle,
                                    chapterIndex,
                                    chapterNumber,
                                    verseNumber,
                                    rootFolder,
                                    chapterCommentators,
                                    isTalmud
                                )
                                synchronized(this@BookProcessor) { processedVerses++ }
                                val progress = (processedVerses.toDouble() / totalVerses * 100).toInt()
                                logger.info("Progress: $progress% ($processedVerses/$totalVerses verses)")
                            }
                        }
                        tasks.add(task)
                    }
                }
            }

            // Attendre que toutes les tâches soient terminées
            tasks.awaitAll()

            // Créer et sauvegarder l'index du livre
            createAndSaveBookIndex(shape, chapterCommentators, rootFolder, isTalmud, subBookTitle)
        }

        logger.info("Completed processing book: $bookTitle")
    }

    private fun isIntroductionChapter(shape: ShapeItem): Boolean {
        return shape.length == 1 && shape.title.contains("Introduction")
    }

    private suspend fun fetchIntroductionVerses(shape: ShapeItem): List<JsonElement> {
        val endpointTitle = shape.title.replace(" ", "%20")
        val textUrl = "$BASE_URL/v3/texts/$endpointTitle"
        val verseJson = fetchJsonFromApi(textUrl)

        val parsedJson = parseJsonOrNull(verseJson, "book ${shape.title}") ?: return emptyList()

        return parsedJson["versions"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")?.jsonArray?.toList() ?: emptyList()
    }

    private suspend fun processIntroductionVerse(
        bookTitle: String,
        chapterNumber: Int,
        verseNumber: Int,
        verseContent: JsonElement,
        shape: ShapeItem,
        chapterCommentators: MutableMap<Int, MutableSet<String>>,
        rootFolder: String,
        isTalmud: Boolean, // Moved from inside to reduce redundant checks
    ) {
        val verseText = extractVerseText(verseContent)
        val endpointTitle = shape.title.replace(" ", "%20")

        // Fetch comments using the isTalmud flag and schema
        val comments = fetchCommentsForVerse(
            endpointTitle,
            chapterNumber,
            verseNumber,
            shape,
            isTalmud = isTalmud,
        )

        // Ensure chapterCommentators is initialized for this chapter
        chapterCommentators.getOrPut(chapterNumber - 1) { mutableSetOf() }.also { commentators ->
            comments.commentary.forEach { commentary ->
                commentators.add(commentary.commentatorName)
            }
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
    }

    @Suppress("DefaultLocale")
    private suspend fun processStandardVerse(
        shape: ShapeItem,
        bookTitle: String,
        chapterIndex: Int,
        chapterNumber: Int,
        verseNumber: Int,
        rootFolder: String,
        chapterCommentators: MutableMap<Int, MutableSet<String>>,
        isTalmud: Boolean
    ) {
        val endpointTitle = shape.title.replace(" ", "%20")
        val chapterWithGuemaraCheck = if (isTalmud) chapterNumber.toGuemaraInt() else chapterNumber
        val formattedVerseNumber = String.format("%010d", verseNumber)


        val textUrl = "$BASE_URL/v3/texts/$endpointTitle%20$chapterWithGuemaraCheck.$formattedVerseNumber"
        val verseJson = fetchJsonFromApi(textUrl)

        val verseResponse = decodeVerseResponseOrNull(verseJson, chapterNumber, verseNumber) ?: return

        val verseText = extractVerseText(verseResponse)

        val comments = fetchCommentsForVerse(
            endpointTitle,
            chapterNumber,
            verseNumber,
            shape,
            isTalmud
        )

        comments.commentary.forEach { commentary ->
            chapterCommentators[chapterIndex]?.add(commentary.commentatorName)
        }

        val verse = Verse(
            number = verseNumber,
            text = verseText,
            targum = comments.targum,
            commentary = comments.commentary,
            quotingCommentary = comments.quotingCommentary,
            reference = comments.reference,
            otherLinks = comments.otherLinks
        )

        saveVerse(bookTitle, chapterNumber, verseNumber, verse, rootFolder)
    }

    private fun parseJsonOrNull(jsonString: String, context: String): JsonObject? {
        return try {
            val parsed = json.parseToJsonElement(jsonString).jsonObject
            if (parsed.containsKey("error")) {
                val errorMsg = parsed["error"]?.jsonPrimitive?.content
                logger.info("Skipping $context due to error: $errorMsg")
                null
            } else {
                parsed
            }
        } catch (e: SerializationException) {
            logger.info("Failed to parse JSON for $context: ${e.message}")
            null
        }
    }

    private fun decodeVerseResponseOrNull(jsonString: String, chapterNumber: Int, verseNumber: Int): VerseResponse? {
        return try {
            json.decodeFromString<VerseResponse>(jsonString)
        } catch (e: SerializationException) {
            logger.info("Failed to deserialize VerseResponse for verse $chapterNumber:$verseNumber: ${e.message}")
            null
        }
    }


    private fun extractVerseText(verseContent: JsonElement): String {
        return when (verseContent) {
            is JsonPrimitive -> verseContent.content
            is JsonArray -> verseContent.jsonArray.joinToString(" ") { it.jsonPrimitive.content }
            else -> ""
        }
    }

    private fun extractVerseText(verseResponse: VerseResponse): String {
        return when (val textElement = verseResponse.versions.firstOrNull()?.text) {
            is JsonPrimitive -> textElement.content
            is JsonArray -> textElement.jsonArray.joinToString(" ") { it.jsonPrimitive.content }
            else -> ""
        }
    }

    private suspend fun createAndSaveBookIndex(
        shape: ShapeItem,
        chapterCommentators: Map<Int, Set<String>>,
        rootFolder: String,
        isTalmud: Boolean,
        subBookTitle: String? = null
    ) {
        val chaptersIndexList = shape.chapters.mapIndexed { chapterIndex, nbVerses ->
            ChapterIndex(
                chapterNumber = chapterIndex + 1,
                numberOfVerses = nbVerses,
                commentators = chapterCommentators[chapterIndex]?.toList() ?: emptyList()
            )
        }

        val bookIndex = BookIndex(
            type = if (isTalmud) BookType.TALMUD else BookType.OTHER,
            title = shape.title,
            heTitle = shape.heBook,
            numberOfChapters = shape.chapters.size,
            chapters = chaptersIndexList,
            sectionNames = translateSections(fetchBookSchema(if (shape.section.isNotEmpty()) shape.title else shape.section)?.sectionNames ?: emptyList())
        )

        // Déterminer le répertoire où sauvegarder l'index
        val indexFolder = if (subBookTitle != null) {
            // Si c'est un sous-livre, sauvegarder dans le répertoire du sous-livre
            "$rootFolder/$subBookTitle"
        } else {
            // Sinon, sauvegarder dans le répertoire du livre principal
            "$rootFolder/${shape.book}"
        }

        val indexFile = File("$indexFolder/index.json")
        if (!indexFile.parentFile.exists()) indexFile.parentFile.mkdirs()
        indexFile.writeText(json.encodeToString(bookIndex))

        logger.info("Index file created for ${shape.book}: ${indexFile.absolutePath}")
    }


    private fun extractSectionNames(node: JsonElement, sections: MutableSet<String>) {
        if (node is JsonObject) {
            node.forEach { (_, value) ->
                when (value) {
                    is JsonObject -> {
                        // Vérifier si le nœud contient "sectionNames"
                        val sectionNames = value["sectionNames"]?.jsonArray
                        sectionNames?.forEach { section ->
                            sections.add(section.jsonPrimitive.content)
                        }
                        // Continuer la récursion
                        extractSectionNames(value, sections)
                    }
                    is JsonArray -> {
                        value.forEach { element ->
                            extractSectionNames(element, sections)
                        }
                    }
                    else -> {
                        // Ignorer les autres types
                    }
                }
            }
        }
    }

    // Fonction pour extraire les addressTypes récursivement
    private fun extractAddressTypes(node: JsonElement, addressTypes: MutableSet<String>) {
        if (node is JsonObject) {
            node.forEach { (_, value) ->
                when (value) {
                    is JsonObject -> {
                        // Vérifier si le nœud contient "addressTypes"
                        val types = value["addressTypes"]?.jsonArray
                        types?.forEach { type ->
                            addressTypes.add(type.jsonPrimitive.content)
                        }
                        // Continuer la récursion
                        extractAddressTypes(value, addressTypes)
                    }
                    is JsonArray -> {
                        value.forEach { element ->
                            extractAddressTypes(element, addressTypes)
                        }
                    }
                    else -> {
                        // Ignorer les autres types
                    }
                }
            }
        }
    }

    private suspend fun fetchBookSchema(bookTitle: String): BookSchema? {
        logger.info("Fetching schema for '$bookTitle'")
        return try {
            val jsonResponse = fetchJsonFromApi("$BASE_URL/v2/raw/index/$bookTitle")
            val jsonElement = Json.parseToJsonElement(jsonResponse)
            val schema = jsonElement.jsonObject["schema"]?.jsonObject

            val addressTypes = schema?.get("addressTypes")?.jsonArray?.map { it.jsonPrimitive.content }
                ?: emptyList()

            val sectionNames = schema?.let { extractSectionNames(it) } ?: emptyList()

            BookSchema(addressTypes, sectionNames)
        } catch (e: Exception) {
            logger.error("Error fetching or parsing JSON for '$bookTitle': ${e.message}", e)
            null
        }
    }

    private fun extractSectionNames(schema: JsonObject): List<String> {
        val sections = mutableListOf<String>()

        // Vérifier si `sectionNames` est présent au niveau actuel
        schema["sectionNames"]?.jsonArray?.map { it.jsonPrimitive.content }?.let {
            sections.addAll(it)
        }

        // Parcourir les nœuds imbriqués
        schema["nodes"]?.jsonArray?.forEach { node ->
            if (node is JsonObject) {
                sections.addAll(extractSectionNames(node))
            } else {
                logger.error("Unexpected element type in 'nodes': ${node::class}")
            }
        }

        return sections
    }


    private suspend fun isTalmud(bookTitle: String): Boolean {
        logger.info("Checking if '$bookTitle' contains 'Talmud' in addressTypes")
        val schema = fetchBookSchema(bookTitle)

        return if (schema != null) {
            val containsTalmud = schema.addressTypes.contains("Talmud")
            logger.info("Result for '$bookTitle': contains 'Talmud' = $containsTalmud")
            containsTalmud
        } else {
            logger.warn("Failed to retrieve schema for '$bookTitle'")
            false
        }
    }

    private fun Int.toGuemaraInt(): String {
        val numberPart = (this + 1) / 2 + 1
        val suffix = if (this % 2 == 1) "a" else "b"
        return "$numberPart$suffix"
    }
}
