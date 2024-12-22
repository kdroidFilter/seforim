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
 * Crée des répertoires et des fichiers basés sur la structure fournie du Table of Content (ToC).
 */
suspend fun createDirectoriesAndFilesWithIndex(rootPath: String, tree: List<TableOfContent>, createBooks: Boolean = true) {
    val logger = LoggerFactory.getLogger("DirectoryCreator")

    val rootDir = initializeRootDirectory(rootPath, logger) ?: return

    val rootNodes = tree.mapNotNull { toc ->
        processCategory(toc, rootDir, rootDir, createBooks, logger) // Passer rootDir comme rootPath
    }

    createIndexFiles(rootDir, rootNodes, logger)
}


/**
 * Initialise le répertoire racine.
 */
private fun initializeRootDirectory(rootPath: String, logger: Logger): File? {
    val rootDir = File(rootPath)
    if (!rootDir.exists()) {
        if (rootDir.mkdirs()) {
            logger.info("Répertoire racine créé : ${rootDir.path}")
        } else {
            logger.error("Impossible de créer le répertoire racine : ${rootDir.path}")
            return null
        }
    }
    return rootDir
}


/**
 * Traite une catégorie dans le ToC.
 */
private suspend fun processCategory(
    toc: TableOfContent,
    rootDir: File,
    rootPath: File, // Utiliser File au lieu de String pour rootPath
    createBooks: Boolean,
    logger: Logger
): DirectoryNode? {
    val categoryName = toc.category?.trim() ?: "Uncategorized"
    val hebrewCategory = toc.heCategory ?: "ללא קטגוריה"

    val categoryDir = File(rootDir, categoryName)

    if (categoryDir.mkdirs()) {
        logger.info("Répertoire de catégorie créé : ${categoryDir.path}")
    } else {
        logger.warn("Échec de la création du répertoire de catégorie ou il existe déjà : ${categoryDir.path}")
    }

    val node = processNode(categoryDir, toc.contents, rootPath, createBooks, logger)
    return node?.let {
        DirectoryNode(
            englishName = categoryName,
            hebrewName = hebrewCategory,
            indexPath = toRelativePath(rootPath, categoryDir),
            children = it.children,
            isLeaf = it.children.isEmpty()
        )
    }
}


/**
 * Traite un nœud de répertoire.
 */
private suspend fun processNode(
    currentPath: File,
    contents: List<ContentItem>?,
    rootPath: File, // Utiliser File au lieu de String
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
            englishName = currentPath.name,
            hebrewName = null, // Les nœuds racines ont leur hebrewName défini dans processCategory
            indexPath = toRelativePath(rootPath, currentPath),
            children = childrenNodes,
            isLeaf = childrenNodes.isEmpty()
        )
    } else null
}


/**
 * Traite un élément de contenu.
 */
private suspend fun processContentItem(
    item: ContentItem,
    currentPath: File,
    rootPath: File, // Utiliser File
    createBooks: Boolean,
    logger: Logger
): DirectoryNode? {
    val (englishName, hebrewName) = determineNodeNames(item)
    logger.debug("Traitement du nœud : $englishName (Hébreu : $hebrewName)")

    if (BLACKLIST.any { it.equals(englishName, ignoreCase = true) }) {
        logger.info("Livre ignoré (présent dans la liste noire) : $englishName")
        return null
    }

    val nodePath = File(currentPath, englishName)

    if (nodePath.mkdirs()) {
        logger.info("Répertoire créé : ${nodePath.path}")
    } else {
        logger.warn("Échec de la création du répertoire ou il existe déjà : ${nodePath.path}")
    }

    return if (item.contents.isNullOrEmpty()) {
        // Traiter le nœud feuille (livre)
        val bookChildren = checkForComplexBook(englishName, nodePath, rootPath, createBooks, logger)

        if (bookChildren.isNotEmpty()) {
            DirectoryNode(
                englishName = englishName,
                hebrewName = hebrewName,
                indexPath = toRelativePath(rootPath, nodePath),
                children = bookChildren,
                isLeaf = false
            )
        } else {
            if (createBooks) {
                runBlocking { buildBookFromShape(englishName, nodePath.path) }
            }
            DirectoryNode(
                englishName = englishName,
                hebrewName = hebrewName,
                indexPath = toRelativePath(rootPath, nodePath),
                children = emptyList(),
                isLeaf = true
            )
        }
    } else {
        // Traiter les nœuds enfants
        val childNode = processNode(nodePath, item.contents, rootPath, createBooks, logger)
        childNode?.copy(
            englishName = englishName,
            hebrewName = hebrewName
        )
    }
}


/**
 * Vérifie si un livre est complexe et traite sa structure si nécessaire.
 */
private suspend fun checkForComplexBook(
    bookTitle: String,
    bookDir: File, // Passer le répertoire du livre
    rootPath: File, // Passer le répertoire racine
    createBooks: Boolean,
    logger: Logger
): List<DirectoryNode> {
    val encodedTitle = bookTitle.replace(" ", "%20")
    logger.info("Vérification si le livre est complexe : $bookTitle")
    val shapeUrl = "$BASE_URL/shape/$encodedTitle"

    // Liste des livres considérés comme complexes
    val complexBooks = listOf("Tur", "Abarbanel on Torah", "Beit Yosef")
    if (bookTitle !in complexBooks) {
        logger.info("Le livre '$bookTitle' n'est pas complexe. Aucune vérification API nécessaire.")
        return emptyList()
    }

    return try {
        val shapeJson = fetchJsonFromApi(shapeUrl)
        val jsonElement = json.parseToJsonElement(shapeJson)

        if (jsonElement.jsonArray.firstOrNull()?.jsonObject?.get("isComplex")?.jsonPrimitive?.boolean == true) {
            logger.info("Structure complexe détectée pour : $bookTitle")
            val complexBook = json.decodeFromString<List<ComplexShapeItem>>(shapeJson).first()

            // Créer des nœuds enfants pour chaque chapitre du livre complexe
            complexBook.chapters.mapNotNull { chapterElement ->
                if (chapterElement is JsonObject) {
                    val chapter = json.decodeFromJsonElement<FlexibleShapeItem>(chapterElement)
                    val chapterDir = File(bookDir, chapter.title)

                    if (createBooks) {
                        // Créer le répertoire du chapitre
                        if (chapterDir.mkdirs()) {
                            logger.info("Répertoire du chapitre créé : ${chapterDir.path}")
                        } else {
                            logger.warn("Échec de la création du répertoire du chapitre ou il existe déjà : ${chapterDir.path}")
                        }
                        // Créer le fichier du chapitre
                        buildBookFromShape(chapter.title, chapterDir.path)
                    }

                    DirectoryNode(
                        englishName = chapter.title,
                        hebrewName = chapter.heTitle,
                        indexPath = toRelativePath(rootPath, chapterDir),
                        children = emptyList(),
                        isLeaf = true
                    )
                } else {
                    logger.warn("Élément de chapitre non valide pour le livre complexe : $bookTitle")
                    null
                }
            }
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        logger.error("Erreur lors de la vérification de la structure complexe pour $bookTitle : ${e.message}")
        emptyList()
    }
}

/**
 * Détermine les noms anglais et hébreu d'un nœud basé sur un `ContentItem`.
 */
private fun determineNodeNames(item: ContentItem): Pair<String, String?> {
    val englishName = when {
        !item.title.isNullOrBlank() -> item.title!!.trim()
        !item.category.isNullOrBlank() -> item.category!!.trim()
        else -> "Untitled"
    }

    val hebrewName = when {
        !item.heTitle.isNullOrBlank() -> item.heTitle!!.trim()
        !item.heCategory.isNullOrBlank() -> item.heCategory!!.trim()
        else -> null
    }

    return Pair(englishName, hebrewName)
}

/**
 * Crée les fichiers d'index en formats JSON et Protobuf.
 */
@OptIn(ExperimentalSerializationApi::class)
private fun createIndexFiles(rootDir: File, rootNodes: List<DirectoryNode?>, logger: Logger) {
    val filteredNodes = rootNodes.filterNotNull()

    val indexFile = File(rootDir, "index.json")
    val jsonContent = json.encodeToString(filteredNodes)
    indexFile.writeText(jsonContent)
    logger.info("Fichier index.json créé : ${indexFile.absolutePath}")

    val protobufData = ProtoBuf.encodeToByteArray(filteredNodes)
    val protobufFile = File(rootDir, "index.proto")
    protobufFile.writeBytes(protobufData)
    logger.info("Fichier index.proto créé : ${protobufFile.absolutePath}")
}

/**
 * Convertit un chemin absolu en chemin relatif basé sur un répertoire racine.
 */
private fun toRelativePath(rootDir: File, file: File): String {
    return rootDir.toURI().relativize(file.toURI()).path
}
