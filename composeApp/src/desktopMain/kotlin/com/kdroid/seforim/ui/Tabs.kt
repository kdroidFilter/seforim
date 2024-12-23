package com.kdroid.seforim.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.SimpleTabContent
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabStrip
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.defaultTabStyle
import kotlin.math.max


@Composable
fun DefaultTabShowcase() {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var tabIds by remember { mutableStateOf((1..3).toList()) }
    val maxId = remember(tabIds) { tabIds.maxOrNull() ?: 0 }

    // Construire la liste des onglets "réels" + l'onglet "Add"
    val tabs = remember(tabIds, selectedTabIndex) {

        // 1) Les onglets "réels"
        val realTabs = tabIds.mapIndexed { index, id ->
            TabData.Default(
                selected = (index == selectedTabIndex),
                content = { tabState ->
                    Column {
                        SimpleTabContent(
                            label = " כרטיסייה $id",
                            state = tabState
                        )
                    }
                },
                onClose = {
                    tabIds = tabIds.toMutableList().apply { removeAt(index) }
                    if (selectedTabIndex >= index) {
                        val maxIndex = max(0, tabIds.lastIndex)
                        selectedTabIndex = (selectedTabIndex - 1).coerceIn(0..maxIndex)
                    }
                },
                onClick = {
                    selectedTabIndex = index
                }
            )
        }

        // 2) L'onglet "Add"
        val addTab = TabData.Default(
            selected = false,          // Il n'est jamais considéré comme "sélectionné"
            closable = false,         // Pas de bouton "Close" sur cet onglet
            content = {
                // Vous pouvez personnaliser l'apparence (icône, texte, etc.)
                Icon(
                    key = AllIconsKeys.General.Add,
                    contentDescription = "Add tab",
                )
            },
            onClose = {},             // onClose n'a pas de sens ici
            onClick = {
                // Ajout d'un nouvel onglet à droite de l'onglet sélectionné
                val insertionIndex = (selectedTabIndex + 1).coerceIn(0..tabIds.size)
                val nextTabId = maxId + 1
                tabIds = tabIds.toMutableList().apply { add(insertionIndex, nextTabId) }
                selectedTabIndex = insertionIndex
            }
        )

        // Combiner les onglets réels + l'onglet "Add" à la fin
        realTabs + addTab
    }

    // Appeler directement TabStrip, qui affichera "vrais onglets + 'Add' " dans la même row
    TabStrip(
        tabs = tabs,
        style = JewelTheme.defaultTabStyle
    )
}
