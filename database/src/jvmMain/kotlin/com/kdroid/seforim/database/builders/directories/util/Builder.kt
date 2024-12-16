package com.kdroid.seforim.database.builders.directories.util

import com.kdroid.seforim.core.model.ContentItem
import com.kdroid.seforim.core.model.DirectoryNode
import com.kdroid.seforim.core.model.TableOfContent
import com.kdroid.seforim.database.common.config.json

import com.kdroid.seforim.database.common.filesutils.saveToProtobuf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.slf4j.LoggerFactory


/**
 * Creates the directory structure and associated files from a content tree,
 * then generates a JSON index describing the hierarchy with relative paths.
 *
 * @param rootPath The root path where the structure will be created.
 * @param tree A list of `TableOfContent` representing the hierarchy.
 */
@OptIn(ExperimentalSerializationApi::class)
fun createDirectoriesAndFilesWithIndex(rootPath: String, tree: List<TableOfContent>) {
    val logger = LoggerFactory.getLogger("DirectoryCreator")

    // Converts an absolute path to a relative path relative to the root
    fun toRelativePath(rootDir: File, file: File): String {
        return rootDir.toURI().relativize(file.toURI()).path
    }

    fun processNode(currentPath: File, contents: List<ContentItem>?): DirectoryNode {
        val childrenNodes = contents?.map { item ->
            val nodeName = item.title ?: item.category ?: "Untitled"
            val nodePath = File(currentPath, nodeName)
            val hebrewTitle = item.heTitle ?: "ללא כותרת"

            if (nodePath.mkdirs()) {
                logger.info("Directory created:  ${nodePath.path}")
            } else {
                logger.warn("Failed to create directory or it already exists: ${nodePath.path}")
            }

            if (item.contents.isNullOrEmpty()) {
                // Leaf node: create shape.json file
                val shapeFilePath = File(nodePath, "shape.json")
                runBlocking {
                    if (!shapeFilePath.exists()) {
                        try {
                            // val shapeJson = fetchJsonFromApi(nodeName, "shape")
                            shapeFilePath.writeText("")
                            logger.info("File created: ${shapeFilePath.path}")
                        } catch (e: Exception) {
                            logger.error("Unable to fetch or write shape.json for: ${nodePath.path}", e)
                        }
                    } else {
                        logger.info("File already exists: ${shapeFilePath.path}")
                    }
                }

                // Return a leaf node with a relative path
                DirectoryNode(
                    name = nodeName,
                    path = toRelativePath(File(rootPath), nodePath),
                    children = emptyList(),
                    isLeaf = true,
                    hebrewTitle = hebrewTitle
                )

            } else {
                // Internal node: recursive processing
                val childNode = processNode(nodePath, item.contents)
                DirectoryNode(
                    name = nodeName,
                    path = toRelativePath(File(rootPath), nodePath),
                    children = childNode.children,
                    isLeaf = false,
                    hebrewTitle = hebrewTitle
                )
            }
        } ?: emptyList()

        return DirectoryNode(
            name = currentPath.name,
            path = toRelativePath(File(rootPath), currentPath),
            children = childrenNodes,
            isLeaf = childrenNodes.isEmpty(),
            hebrewTitle = null
        )
    }

    val rootDir = File(rootPath)
    if (!rootDir.exists()) {
        if (rootDir.mkdirs()) {
            logger.info("Root directory created: ${rootDir.path}")
        } else {
            logger.error("Unable to create root directory: ${rootDir.path}")
        }
    }

    val rootNodes = tree.map { toc ->
        val categoryName = toc.category ?: "Uncategorized"
        val categoryDir = File(rootDir, categoryName)
        val hebrewCategory = toc.heCategory ?: "ללא קטגוריה"

        if (categoryDir.mkdirs()) {
            logger.info("Category directory created: ${categoryDir.path}")
        } else {
            logger.warn("Failed to create category directory or it already exists: ${categoryDir.path}")
        }

        // Process children and retrieve a node representing this category
        val node = processNode(categoryDir, toc.contents)
        DirectoryNode(
            name = categoryName,
            path = toRelativePath(File(rootPath), categoryDir),
            children = node.children,
            isLeaf = node.children.isEmpty(),
            hebrewTitle = hebrewCategory
        )
    }

    // Serialize to JSON with relative paths
    val indexFile = File(rootDir, "index.json")
    val jsonContent = json.encodeToString(rootNodes)
    indexFile.writeText(jsonContent)
    logger.info("index.json file created: ${indexFile.absolutePath}")

    val protobufData = ProtoBuf.encodeToByteArray(rootNodes)
    // Save the data to a file
    File(rootDir,"index.proto").writeBytes(protobufData)
    logger.info("index.proto file created: ${indexFile.absolutePath}")

}
