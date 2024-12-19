import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.kdroid.seforim.ui.*
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import java.awt.Cursor


@Composable
fun SearchTextField() {
    val state2 = rememberTextFieldState("")
    TextField(
        state = state2,
        placeholder = { Text("חפש ספר") },
        modifier = Modifier.width(200.dp).padding(8.dp),
        leadingIcon = {
            Icon(
                key = AllIconsKeys.Actions.Find,
                contentDescription = "SearchIcon",
                modifier = Modifier.size(16.dp).padding(end = 2.dp),
            )
        }
    )
}

private fun Modifier.cursorForHorizontalResize(): Modifier =
    pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun SplitLayouts(bookTitle: String) {
    Column(Modifier.fillMaxSize()) {
        val splitterState = rememberSplitPaneState()
        HorizontalSplitPane(
            splitPaneState = splitterState
        ) {
            first(150.dp) {
                Column {
                    SearchTextField()
                    DisplayTree()
                }
            }
            second(50.dp) {
                val bookIndex = loadBookIndex(bookTitle)
                BookViewScreen(bookIndex = bookIndex)
            }
            splitter {
                visiblePart {
                    Box(
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(JewelTheme.globalColors.borders.disabled)

                    )
                }
                handle {
                    Box(
                        Modifier
                            .markAsHandle()
                            .cursorForHorizontalResize()
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(JewelTheme.globalColors.borders.disabled)
                    )
                }
            }
        }
    }
}

