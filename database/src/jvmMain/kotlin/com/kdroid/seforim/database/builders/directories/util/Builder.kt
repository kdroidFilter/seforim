package com.kdroid.seforim.database.builders.directories.util

import com.kdroid.seforim.database.builders.directories.api.fetchJsonFromApi
import com.kdroid.seforim.database.builders.tableofcontent.model.ContentItem
import com.kdroid.seforim.database.builders.tableofcontent.model.TableOfContent
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Creates a directory structure and associated files based on a given tree of content.
 *
 * @param rootPath The root directory path where the structure will be created.
 * @param tree A list of `TableOfContent` representing the hierarchical structure of directories and files to create.
 */
fun createDirectoriesAndFiles(rootPath: String, tree: List<TableOfContent>) {
    val logger = org.slf4j.LoggerFactory.getLogger("DirectoryCreator")

    fun processNode(currentPath: File, contents: List<ContentItem>?) {
        contents?.forEach { item ->
            val nodeName = item.title ?: item.category ?: "Untitled" // Utilise category si title est absent
            val nodePath = File(currentPath, nodeName)

            if (nodePath.mkdirs()) {
                logger.info("Created directory:  ${nodePath.path}")
            } else {
                logger.warn("Failed to create directory or it already exists: ${nodePath.path}")
            }

            if (item.contents.isNullOrEmpty()) {
                // Fetch JSON data if it's a leaf and save it in shape.json
                val shapeFilePath = File(nodePath, "shape.json")
                runBlocking {
                    if (!shapeFilePath.exists()) {
                        try {
                            val shapeJson = fetchJsonFromApi(nodeName, "shape")
                            shapeFilePath.writeText(shapeJson)
                            logger.info("Created file: ${shapeFilePath.path}")
                        } catch (e: Exception) {
                            logger.error("Failed to fetch or write shape.json for: \${nodePath.path}", e)
                        }
                    } else {
                        logger.info("File already exists: ${shapeFilePath.path}")
                    }
                }
            } else {
                // Create a directory and process children
                processNode(nodePath, item.contents)
            }
        }
    }

    val rootDir = File(rootPath)
    if (!rootDir.exists()) {
        if (rootDir.mkdirs()) {
            logger.info("Created root directory: ${rootDir.path}")
        } else {
            logger.error("Failed to create root directory: ${rootDir.path}")
        }
    }

    tree.forEach { toc ->
        val categoryName = toc.category ?: "Uncategorized"
        val categoryDir = File(rootDir, categoryName)

        if (categoryDir.mkdirs()) {
            logger.info("Created category directory: ${categoryDir.path}")
        } else {
            logger.warn("Failed to create category directory or it already exists: ${categoryDir.path}")
        }

        processNode(categoryDir, toc.contents)
    }
}
