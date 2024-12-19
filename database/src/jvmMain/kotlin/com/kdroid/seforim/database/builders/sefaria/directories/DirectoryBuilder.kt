package com.kdroid.seforim.database.builders.sefaria.directories

import com.kdroid.seforim.core.model.ContentItem
import com.kdroid.seforim.core.model.DirectoryNode
import com.kdroid.seforim.core.model.TableOfContent
import com.kdroid.seforim.database.builders.sefaria.book.api.fetchJsonFromApi
import com.kdroid.seforim.database.builders.sefaria.book.model.ComplexShapeItem
import com.kdroid.seforim.database.builders.sefaria.book.model.FlexibleShapeItem
import com.kdroid.seforim.database.builders.sefaria.book.util.buildBookFromShape
import com.kdroid.seforim.database.common.config.json
import com.kdroid.seforim.database.common.constants.BASE_URL
import com.kdroid.seforim.database.common.constants.BLACKLIST

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.File
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.*
import kotlinx.serialization.protobuf.ProtoBuf
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Creates directories and files based on the provided Table of Content (ToC) structure.
 * This function initializes a root directory, processes each category in the ToC to create directories,
 * and generates index files in JSON and Protobuf formats. Optionally, book-related files can also be created.
 *
 * @param rootPath The path to the root directory where directories and files will be created.
 * @param tree A list of TableOfContent objects representing the structure of categories and their contents.
 * @param createBooks A boolean flag indicating whether book-related files should be created.
 *                    Defaults to true.
 */
suspend fun createDirectoriesAndFilesWithIndex(rootPath: String, tree: List<TableOfContent>, createBooks: Boolean = true) {
    val logger = LoggerFactory.getLogger("DirectoryCreator")

    val rootDir = initializeRootDirectory(rootPath, logger) ?: return

    val rootNodes = tree.mapNotNull { toc ->
        processCategory(toc, rootDir, rootPath, createBooks, logger)
    }

    createIndexFiles(rootDir, rootNodes, logger)
}

/**
 * Initializes the root directory at the specified path. If the directory does not exist, it attempts to create it.
 * Logs the appropriate messages for directory creation success or failure.
 *
 * @param rootPath The path to the root directory that needs to be initialized.
 * @param logger The logger instance used to log messages during the initialization process.
 * @return The initialized `File` object for the root directory if the directory exists or is successfully created,
 *         or `null` if the directory creation fails.
 */
private fun initializeRootDirectory(rootPath: String, logger: Logger): File? {
    val rootDir = File(rootPath)
    if (!rootDir.exists()) {
        if (rootDir.mkdirs()) {
            logger.info("Root directory created: ${rootDir.path}")
        } else {
            logger.error("Unable to create the root directory: ${rootDir.path}")
            return null
        }
    }
    return rootDir
}

/**
 * Processes a category within a Table of Content (ToC) structure, creates the required directory for the category,
 * and processes its contents to generate a corresponding node representation.
 *
 * @param toc The TableOfContent object containing details of the category and its contents.
 * @param rootDir The root directory where the category directory will be created.
 * @param rootPath The path to the root directory, used for relative path calculations.
 * @param createBooks A boolean indicating whether book-related files should be processed.
 * @param logger The logger used for logging operations and warnings during processing.
 * @return A DirectoryNode representing the processed category and its contents, or null if no contents were processed.
 */
private suspend fun processCategory(
    toc: TableOfContent,
    rootDir: File,
    rootPath: String,
    createBooks: Boolean,
    logger: Logger
): DirectoryNode? {
    val categoryName = toc.category?.trim() ?: "Uncategorized"
    val categoryDir = File(rootDir, categoryName)
    val hebrewCategory = toc.heCategory ?: "ללא קטגוריה"

    if (categoryDir.mkdirs()) {
        logger.info("Category directory created: ${categoryDir.path}")
    } else {
        logger.warn("Failed to create category directory or it already exists: ${categoryDir.path}")
    }

    val node = processNode(categoryDir, toc.contents, rootPath, createBooks, logger)
    return node?.let {
        DirectoryNode(
            name = categoryName,
            path = toRelativePath(rootDir, categoryDir),
            children = it.children,
            isLeaf = it.children.isEmpty(),
            hebrewTitle = hebrewCategory
        )
    }
}

/**
 * Processes a directory node by traversing its contents, creating child directory nodes
 * recursively, and constructing a `DirectoryNode` representation.
 *
 * @param currentPath The current file path representing the directory node being processed.
 * @param contents A list of `ContentItem` objects representing the contents of the directory, or null if none exist.
 * @param rootPath The path to the root directory, used for relative path calculations.
 * @param createBooks A boolean indicating whether books should be created when processing leaf nodes.
 * @param logger The logger used for logging operations during processing.
 * @param isParentExcluded A boolean to signify if the parent directory is excluded from processing.
 *                          Defaults to `false`.
 *
 * @return A `DirectoryNode` representing the processed directory and its children, or null if
 *         the directory or its children are excluded or empty.
 */
private suspend fun processNode(
    currentPath: File,
    contents: List<ContentItem>?,
    rootPath: String,
    createBooks: Boolean,
    logger: Logger,
    isParentExcluded: Boolean = false
): DirectoryNode? {
    if (isParentExcluded) return null

    val childrenNodes = contents?.mapNotNull { item ->
        processContentItem(item, currentPath, rootPath, createBooks, logger)
    } ?: emptyList()

    return if (childrenNodes.isNotEmpty()) {
        DirectoryNode(
            name = currentPath.name,
            path = toRelativePath(File(rootPath), currentPath),
            children = childrenNodes,
            isLeaf = childrenNodes.isEmpty(),
            hebrewTitle = null
        )
    } else null
}


/**
 * Processes a content item by creating its corresponding directory structure and handling its data.
 *
 * This method validates whether a content item is blacklisted, attempts to create a directory
 * for the item, and processes it recursively if it has children. For items without children,
 * it checks for complex book structures and creates books if required.
 *
 * @param item The `ContentItem` object to be processed.
 * @param currentPath The current `File` object representing the directory path being processed.
 * @param rootPath The root path as a `String` used for determining relative paths.
 * @param createBooks A `Boolean` flag indicating whether to generate books where applicable.
 * @param logger A `Logger` instance used for logging information, warnings, and errors during the process.
 * @return A `DirectoryNode` representing the processed directory structure, or `null` if the item is blacklisted.
 */
private suspend fun processContentItem(
    item: ContentItem,
    currentPath: File,
    rootPath: String,
    createBooks: Boolean,
    logger: Logger
): DirectoryNode? {
    val nodeName = determineNodeName(item)
    logger.debug("Checking node: $nodeName")

    if (BLACKLIST.any { it.equals(nodeName, ignoreCase = true) }) {
        logger.info("Book ignored (present in the blacklist): $nodeName")
        return null
    }

    val nodePath = File(currentPath, nodeName)
    val hebrewTitle = item.heTitle ?: item.heCategory

    if (nodePath.mkdirs()) {
        logger.info("Directory created: ${nodePath.path}")
    } else {
        logger.warn("Failed to create directory or it already exists: ${nodePath.path}")
    }

    return if (item.contents.isNullOrEmpty()) {
        // Check if it is a complex book
        val bookChildren = checkForComplexBook(nodeName, nodePath.path, createBooks, logger)

        if (bookChildren.isNotEmpty()) {
            DirectoryNode(
                name = nodeName,
                path = toRelativePath(File(rootPath), nodePath),
                children = bookChildren,
                isLeaf = false,
                hebrewTitle = hebrewTitle
            )
        } else {
            if (createBooks) {
                runBlocking { buildBookFromShape(nodeName, nodePath.parent) }
            }
            DirectoryNode(
                name = nodeName,
                path = toRelativePath(File(rootPath), nodePath),
                children = emptyList(),
                isLeaf = true,
                hebrewTitle = hebrewTitle
            )
        }
    } else {
        processNode(nodePath, item.contents, rootPath, createBooks, logger)
    }
}

/**
 * Checks whether the specified book is complex and processes its structure if necessary.
 * This function determines if the structure of a book is complex*/
private suspend fun checkForComplexBook(
    bookTitle: String,
    rootFolder: String,
    createBooks: Boolean,
    logger: Logger
): List<DirectoryNode> {
    val encodedTitle = bookTitle.replace(" ", "%20")
    logger.info("Checking if book is complex: $bookTitle")
    val shapeUrl = "$BASE_URL/shape/$encodedTitle"


    //DEBUG
    if ((bookTitle != "Tur") && (bookTitle != "Abarbanel on Torah")) {
        logger.info("The book '$bookTitle' is not complex. No API check needed.")
        return emptyList()
    }

    return try {
        val shapeJson = fetchJsonFromApi(shapeUrl)
        val jsonElement = json.parseToJsonElement(shapeJson)

        //TODO Unify the detection of complex books with that of the book builder
        if (jsonElement.jsonArray.firstOrNull()?.jsonObject?.get("isComplex")?.jsonPrimitive?.boolean == true) {
            logger.info("Detected complex book structure for: $bookTitle")
            val complexBook = json.decodeFromString<List<ComplexShapeItem>>(shapeJson).first()

            // Create child nodes for each chapter of the complex book
            complexBook.chapters.mapNotNull { chapterElement ->
                when (chapterElement) {
                    is JsonObject -> {
                        val chapter = json.decodeFromJsonElement<FlexibleShapeItem>(chapterElement)
                        val chapterPath = "${rootFolder}/${chapter.title}"

                        if (createBooks) {
                            // Create the chapter file
                            File(chapterPath).mkdirs()
                            buildBookFromShape(chapter.title, rootFolder)
                        }

                        DirectoryNode(
                            name = chapter.title,
                            path = chapterPath,
                            children = emptyList(),
                            isLeaf = true,
                            hebrewTitle = chapter.heTitle
                        )
                    }
                    else -> null
                }
            }
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        logger.error("Error checking complex book structure for $bookTitle: ${e.message}")
        emptyList()
    }
}

/**
 * Determines the name of a node based on the attributes of a given `ContentItem`.
 *
 * The method prioritizes the `title` field of the `ContentItem` and trims whitespace for a valid name.
 * If the `title` is not available or blank, it falls back to the `category` field.
 * If neither the `title` nor the `category` is provided, it returns a default value of "Untitled".
 *
 * @param item The `ContentItem` whose attributes are used to determine the node name.
 * @return A `String` representing the determined node name. If none of the fields are available, returns "Untitled".
 */
private fun determineNodeName(item: ContentItem): String {
    return when {
        !item.title.isNullOrBlank() -> item.title!!.trim()
        !item.category.isNullOrBlank() -> item.heCategory!!.trim()
        else -> "Untitled"
    }
}

/**
 * Creates index files in JSON and Protobuf formats based on the provided root directory and list of directory nodes.
 * These files help in representing the structure of directories and their contents in various formats.
 *
 * @param rootDir The root directory where the index files will be created.
 * @param rootNodes A list of `DirectoryNode` objects representing the hierarchy to be serialized into the index files.
 * @param logger The logger instance used to log messages about the creation of index files.
 */
@OptIn(ExperimentalSerializationApi::class)
private fun createIndexFiles(rootDir: File, rootNodes: List<DirectoryNode?>, logger: Logger) {
    val indexFile = File(rootDir, "index.json")
    val jsonContent = json.encodeToString(rootNodes)
    indexFile.writeText(jsonContent)
    logger.info("index.json file created: ${indexFile.absolutePath}")

    val protobufData = ProtoBuf.encodeToByteArray(rootNodes)
    val protobufFile = File(rootDir, "index.proto")
    protobufFile.writeBytes(protobufData)
    logger.info("index.proto file created: ${protobufFile.absolutePath}")
}

/**
 * Converts the absolute path of a file to a relative path based on a specified root directory.
 *
 * @param rootDir The root directory from which the relative path should be calculated.
 * @param file The target file for which the relative path is to be determined.
 * @return A string representing the relative path of the target file with respect to the root directory.
 */
private fun toRelativePath(rootDir: File, file: File): String {
    return rootDir.toURI().relativize(file.toURI()).path
}
