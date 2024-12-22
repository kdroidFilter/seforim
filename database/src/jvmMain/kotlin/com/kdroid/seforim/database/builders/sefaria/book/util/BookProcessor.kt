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
 * Classe responsable du traitement des livres en récupérant les versets,
 * les commentaires, en sauvegardant les données et en créant des index.
 */
class BookProcessor {

    /**
     * Orchestration principale pour traiter un livre simple.
     *
     * @param shape L'objet ShapeItem représentant la structure du livre.
     * @param rootFolder Le répertoire racine où les données seront
     *    sauvegardées.
     */
    internal suspend fun processSimpleBook(
        shape: ShapeItem,
        rootFolder: String,
        subBookTitle: String? = null
    ) {
        val totalVerses = shape.chapters.sum()
        var processedVerses = 0
        val bookTitle = shape.title
        val isTalmud = isTalmud(shape.book)

        logger.info("Processing book: $bookTitle")

        val chapterCommentators = mutableMapOf<Int, MutableSet<String>>()

        // Fonction pour déterminer si l'offset doit être appliqué
        suspend fun shouldApplyOffset(): Boolean {
            val url = "$BASE_URL/v2/raw/index/${shape.book}"
            logger.info("Fetching JSON data from URL: $url")

            // Fetch the JSON string from the API
            val jsonString = try {
                fetchJsonFromApi(url)
            } catch (e: Exception) {
                logger.error("Failed to fetch data from $url: ${e.message}", e)
                return false
            }

            logger.debug("Raw JSON response from $url: ${jsonString.take(200)}") // Log the first 200 characters of the response

            // Parse the JSON string into a JsonElement
            val jsonElement: JsonElement = try {
                Json.parseToJsonElement(jsonString)
            } catch (e: Exception) {
                logger.error("Failed to parse JSON from $url: ${e.message}", e)
                return false
            }

            logger.debug("Successfully parsed JSON data from $url")

            // Navigate through the JSON structure to find "index_offsets_by_depth"
            val schema = jsonElement.jsonObject["schema"]
            if (schema == null) {
                logger.warn("Key 'schema' not found in JSON from $url")
                return false
            }

            val nodes = schema.jsonObject["nodes"]?.jsonArray
            if (nodes == null) {
                logger.warn("Key 'nodes' not found or is not an array in 'schema' from $url")
                return false
            }

            val hasIndexOffsets = nodes.any { node ->
                val containsKey = node.jsonObject.containsKey("index_offsets_by_depth")
                logger.debug("Checking node: {}. Contains 'index_offsets_by_depth': {}", node.jsonObject, containsKey)
                containsKey
            }

            if (hasIndexOffsets) {
                logger.info("'index_offsets_by_depth' key exists in one or more nodes")
            } else {
                logger.info("'index_offsets_by_depth' key not found in any node")
            }

            return hasIndexOffsets
        }


        // Fonction pour calculer l'offset
        fun calculateOffset(chapterIndex: Int): Int {
            return shape.chapters.take(chapterIndex).sum()
        }

        coroutineScope {
            val semaphore = Semaphore(20)
            val tasks = mutableListOf<Deferred<Unit>>()

            shape.chapters.forEachIndexed { chapterIndex, nbVerses ->
                val chapterNumber = chapterIndex + 1
                chapterCommentators[chapterIndex] = mutableSetOf()
                logger.info("Processing chapter $chapterNumber with $nbVerses verses")

                // Calculer l'offset uniquement si nécessaire
                val offset = if (shouldApplyOffset()) calculateOffset(chapterIndex) else 0

                if (isIntroductionChapter(shape)) {
                    val task = async {
                        val verses = fetchIntroductionVerses(shape)
                        verses.forEachIndexed { verseIndex, verseContent ->
                            semaphore.withPermit {
                                processIntroductionVerse(
                                    bookTitle = shape.title,
                                    chapterNumber = chapterNumber,
                                    verseNumber = verseIndex + 1,
                                    verseContent = verseContent,
                                    shape = shape,
                                    chapterCommentators = chapterCommentators,
                                    rootFolder = rootFolder,
                                    isTalmud = isTalmud,
                                    offset = offset
                                )
                                synchronized(this@BookProcessor) { processedVerses++ }
                                logger.info("Processed verse $chapterNumber:${verseIndex + 1 + offset}")
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
                                    shape = shape,
                                    bookTitle = bookTitle,
                                    chapterIndex = chapterIndex,
                                    chapterNumber = chapterNumber,
                                    verseNumber = verseNumber + offset, // Ajout de l'offset conditionnel
                                    rootFolder = rootFolder,
                                    chapterCommentators = chapterCommentators,
                                    isTalmud = isTalmud
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
            createAndSaveBookIndex(
                shape = shape,
                chapterCommentators = chapterCommentators,
                rootFolder = rootFolder,
                isTalmud = isTalmud,
                subBookTitle = subBookTitle, shouldApplyOffset = shouldApplyOffset()
            )
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
        isTalmud: Boolean,
        offset: Int
    ) {
        val verseText = extractVerseText(verseContent)
        val endpointTitle = shape.title.replace(" ", "%20")

        // Fetch comments using the isTalmud flag and schema
        val comments = fetchCommentsForVerse(
            bookTitle = endpointTitle,
            chapter = chapterNumber,
            verse = verseNumber + offset,
            shape = shape,
            isTalmud = isTalmud,
        )

        // Ensure chapterCommentators is initialized for this chapter
        chapterCommentators.getOrPut(chapterNumber - 1) { mutableSetOf() }.also { commentators ->
            comments.commentary.forEach { commentary ->
                commentators.add(commentary.commentatorName)
            }
        }

        val verse = Verse(
            number = verseNumber + offset,
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
        subBookTitle: String? = null,
        shouldApplyOffset: Boolean
    ) {
        val cumulativeOffsets = shape.chapters.runningFold(0) { acc, nbVerses -> acc + nbVerses }

        val chaptersIndexList = shape.chapters.mapIndexed { chapterIndex, nbVerses ->
            ChapterIndex(
                chapterNumber = chapterIndex + 1,
                offset = if (shouldApplyOffset) cumulativeOffsets[chapterIndex] else 0,
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
            sectionNames = translateSections(
                fetchBookSchema(if (shape.section.isNotEmpty()) shape.title else shape.section)?.sectionNames
                    ?: emptyList()
            )
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
