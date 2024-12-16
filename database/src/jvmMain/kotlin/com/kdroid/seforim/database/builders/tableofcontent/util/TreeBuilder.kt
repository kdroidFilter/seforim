package com.kdroid.seforim.database.builders.tableofcontent.util

import com.kdroid.seforim.core.model.ContentItem
import com.kdroid.seforim.core.model.TableOfContent

import org.jetbrains.jewel.foundation.lazy.tree.ChildrenGeneratorScope
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.lazy.tree.buildTree

 fun convertToTree(data: List<TableOfContent>): Tree<String> {
    return buildTree {
        data.forEach { toc ->
            addNode(toc.category ?: "Uncategorized") {
                buildContentTree(this, toc.contents)
            }
        }
    }
}

internal fun buildContentTree(treeBuilder: ChildrenGeneratorScope<String>, contents: List<ContentItem>?) {
    contents?.forEach { content ->
        val nodeName = content.title ?: content.category ?: "Untitled"
        if (content.contents.isNullOrEmpty()) {
            // Dernier enfant : utiliser addLeaf
            treeBuilder.addLeaf(nodeName)
        } else {
            // Nœud intermédiaire : utiliser addNode
            treeBuilder.addNode(nodeName) {
                buildContentTree(this, content.contents)
            }
        }
    }
}