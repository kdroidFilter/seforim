package com.kdroid.seforim.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.window.DecoratedWindowScope
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.newFullscreenControls

@Composable
fun DecoratedWindowScope.TitleBarView() {
    TitleBar(Modifier.newFullscreenControls() ) {
        Row(Modifier.align(Alignment.Start).padding(end = 32.dp)) {
            DefaultTabShowcase()
        }
    }
}
