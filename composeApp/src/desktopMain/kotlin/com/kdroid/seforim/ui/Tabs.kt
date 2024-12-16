package com.kdroid.seforim.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.defaultTabStyle
import kotlin.math.max


@Composable
fun DefaultTabShowcase() {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    var tabIds by remember { mutableStateOf((1..3).toList()) }
    val maxId = remember(tabIds) { tabIds.maxOrNull() ?: 0 }

    val tabs =
        remember(tabIds, selectedTabIndex) {
            tabIds.mapIndexed { index, id ->
                TabData.Default(
                    selected = index == selectedTabIndex,
                    content = { tabState ->
                        Column {
                            SimpleTabContent(label = " כרטיסייה $id", state = tabState)

                        }
                    },
                    onClose = {
                        tabIds = tabIds.toMutableList().apply { removeAt(index) }
                        if (selectedTabIndex >= index) {
                            val maxPossibleIndex = max(0, tabIds.lastIndex)
                            selectedTabIndex = (selectedTabIndex - 1).coerceIn(0..maxPossibleIndex)
                        }
                    },
                    onClick = { selectedTabIndex = index },
                )
            }
        }

    TabStripWithAddButton(tabs, JewelTheme.defaultTabStyle) {
        val insertionIndex = (selectedTabIndex + 1).coerceIn(0..tabIds.size)
        val nextTabId = maxId + 1

        tabIds = tabIds.toMutableList().apply { add(insertionIndex, nextTabId) }
        selectedTabIndex = insertionIndex
    }
}

@Composable
private fun TabStripWithAddButton(tabs: List<TabData>, style: TabStyle, onAddClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TabStrip(tabs, style, modifier = Modifier.weight(1f))
        IconButton(onClick = onAddClick, modifier = Modifier.size(JewelTheme.defaultTabStyle.metrics.tabHeight)) {
            Icon(key = AllIconsKeys.General.Add, contentDescription = "Add a tab")
        }
    }
}
