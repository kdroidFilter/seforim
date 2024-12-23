package com.kdroid.seforim

import SplitLayouts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.application
import com.kdroid.seforim.ui.TitleBarView
import com.kdroid.seforim.utils.DarkModeDetector
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.*
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.lightWithLightHeader
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.styling.TitleBarStyle

fun main() {
    application {
        var isDarkTheme by remember { mutableStateOf(DarkModeDetector.isDarkThemeUsed) }

        // Register the listener for theme changes
        DarkModeDetector.registerListener { isDarkTheme = it }

        val textStyle = JewelTheme.createDefaultTextStyle()
        val editorStyle = JewelTheme.createEditorTextStyle()

        val themeDefinition = if (isDarkTheme) {
            JewelTheme.darkThemeDefinition(defaultTextStyle = textStyle, editorTextStyle = editorStyle)
        } else {
            JewelTheme.lightThemeDefinition(defaultTextStyle = textStyle, editorTextStyle = editorStyle)
        }

        IntUiTheme(
            theme = themeDefinition,
            styling = ComponentStyling.default().decoratedWindow(
                titleBarStyle = if (isDarkTheme) TitleBarStyle.dark() else TitleBarStyle.lightWithLightHeader()
            ),
            swingCompatMode = false,
        ) {

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                DecoratedWindow(
                    onCloseRequest = { exitApplication() },
                    title = "Seforim",
                    content = {
                        TitleBarView()
                        Column(
                            Modifier.trackActivation().fillMaxSize().background(JewelTheme.globalColors.panelBackground)
                        ) { SplitLayouts("Amos")
                        }
                    },
                )
            }
        }

    }
}