package com.kdroid.seforim.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kdroid.seforim.core.model.DirectoryNode
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.lazy.tree.ChildrenGeneratorScope
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.lazy.tree.buildTree
import org.jetbrains.jewel.foundation.lazy.tree.emptyTree
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.styling.defaults
import org.jetbrains.jewel.ui.component.LazyTree
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.treeStyle
import java.io.File

@OptIn(ExperimentalSerializationApi::class)
internal fun readDirectoryNodesFromProtobuf(fileName: String): List<DirectoryNode> {
    // Navigate to the database module directory relatively
    val basePath = File("../database/generated/$fileName")
    val fileBytes = basePath.readBytes()
    return ProtoBuf.decodeFromByteArray(ListSerializer(DirectoryNode.serializer()), fileBytes)
}


fun convertDirectoryNodesToTree(data: List<DirectoryNode>): Tree<DirectoryNode> {
    return buildTree {
        data.forEach { node ->
            addNode(node) {
                buildDirectoryNodeTree(this, node.children)
            }
        }
    }
}

internal fun buildDirectoryNodeTree(treeBuilder: ChildrenGeneratorScope<DirectoryNode>, children: List<DirectoryNode>) {
    children.forEach { childNode ->
        if (childNode.children.isEmpty()) {
            treeBuilder.addLeaf(childNode)
        } else {
            treeBuilder.addNode(childNode) {
                buildDirectoryNodeTree(this, childNode.children)
            }
        }
    }
}

@OptIn(ExperimentalJewelApi::class)
@Composable
fun DisplayTree() {
    var tree by remember { mutableStateOf<Tree<DirectoryNode>>(emptyTree()) }

    LaunchedEffect(Unit) {
        val nodes = readDirectoryNodesFromProtobuf("index.proto")
        tree = convertDirectoryNodesToTree(nodes)
    }

    val layoutDirection = LocalLayoutDirection.current

    val treeIcons = remember(layoutDirection) {
            LazyTreeIcons(
                chevronCollapsed = AllIconsKeys.General.ChevronLeft,
                chevronExpanded = AllIconsKeys.General.ChevronDown,
                chevronSelectedCollapsed = AllIconsKeys.General.ChevronLeft,
                chevronSelectedExpanded = AllIconsKeys.General.ChevronDown,
            )
    }

    val defaultTreeStyle = LazyTreeStyle(
        metrics = LazyTreeMetrics.defaults(),
        icons = treeIcons,
        colors = SimpleListItemColors(
            contentFocused = JewelTheme.treeStyle.colors.contentFocused,
            content = JewelTheme.treeStyle.colors.content,
            backgroundFocused = JewelTheme.treeStyle.colors.backgroundFocused,
            backgroundSelected = JewelTheme.treeStyle.colors.backgroundSelected,
            backgroundSelectedFocused = JewelTheme.treeStyle.colors.backgroundSelectedFocused,
            contentSelected = JewelTheme.treeStyle.colors.contentSelected,
            contentSelectedFocused = JewelTheme.treeStyle.colors.contentSelectedFocused
        )
    )

    CompositionLocalProvider(LocalLazyTreeStyle provides defaultTreeStyle) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            LazyTree(
                tree = tree,
                modifier = Modifier.fillMaxSize(),
                onElementClick = { element ->
                    val directoryNode = element.data
                    println("Path: ${directoryNode.path}")
                },
                onElementDoubleClick = { element ->
                    val directoryNode = element.data
                    println("ID: ${directoryNode.name}")
                }
            ) { element ->
                    Text(element.data.hebrewTitle ?: element.data.name, Modifier.padding(2.dp))

            }
        }
    }
}
