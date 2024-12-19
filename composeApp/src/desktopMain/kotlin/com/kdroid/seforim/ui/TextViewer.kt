package com.kdroid.seforim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import be.digitalia.compose.htmlconverter.htmlToAnnotatedString
import com.kdroid.gematria.converter.toHebrewNumeral
import com.kdroid.seforim.constants.GENERATED_FOLDER
import com.kdroid.seforim.core.model.BookIndex
import com.kdroid.seforim.core.model.CommentaryBase
import com.kdroid.seforim.core.model.Verse
import kotlinx.serialization.json.Json
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import seforim.composeapp.generated.resources.`Guttman David_Bold`
import seforim.composeapp.generated.resources.Res
import java.io.File

fun loadBookIndex(bookTitle: String): BookIndex {
    val path = "../database/$GENERATED_FOLDER/$bookTitle/index.json"
    val file = File(path)
    val indexJson = file.readText()
    return Json.decodeFromString<BookIndex>(indexJson)
}


@Composable
fun BookViewScreen(bookIndex: BookIndex) {
    // Default states for the selected chapter and verse
    val defaultChapter = 1
    val defaultVerse = 1
    var currentChapter by remember { mutableStateOf(defaultChapter) }
    var currentVerse by remember { mutableStateOf(defaultVerse) }

    // Load data for the current verse
    val currentVerseData by produceState<Verse?>(initialValue = null, currentChapter, currentVerse) {
        value = loadVerse(bookIndex.title, currentChapter, currentVerse)
    }

    // List of chapters
    val chapters = remember(bookIndex) {
        bookIndex.chapters
            .map { it.chapterNumber }
            .filter { it >= 0 } // Filtrer pour exclure les nombres négatifs
    }

    // List of verses for the selected chapter
    val verses = remember(currentChapter, bookIndex) {
        bookIndex.chapters
            .firstOrNull { it.chapterNumber == currentChapter }
            ?.let { chapter -> (1..chapter.numberOfVerses).map { it } }
            ?: emptyList()
    }

    // States for the two SelectableLazyColumns
    val chapterListState = rememberSelectableLazyListState()
    val verseListState = rememberSelectableLazyListState()

    Row(modifier = Modifier.fillMaxSize()) {
        // Left column for chapters and verses
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
        ) {
            // First SelectableLazyColumn: List of chapters
            Text("פרקים", fontSize = 12.sp, modifier = Modifier)
            SelectableLazyColumn(
                modifier = Modifier.weight(1f),
                selectionMode = SelectionMode.Single,
                state = chapterListState,
                onSelectedIndexesChange = { selectedIndexes ->
                    selectedIndexes.firstOrNull()?.let { index ->
                        currentChapter = chapters[index]
                        currentVerse = 1 // Reset verse to 1
                    }
                },
                contentPadding = PaddingValues(8.dp),
                content = {
                    items(
                        count = chapters.size,
                        key = { index -> "chapter_${chapters[index]}" }
                    ) { index ->
                        val chapterNumber = chapters[index]

                        if (chapterNumber == 0) return@items
                        val isSelected = chapterNumber == currentChapter

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) JewelTheme.globalColors.outlines.focused else Color.Transparent
                                )
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "פרק ${chapterNumber.toHebrewNumeral()}",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            )


            // Second SelectableLazyColumn: List of verses
            Text("פסוקים", fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            SelectableLazyColumn(
                modifier = Modifier.weight(1f),
                selectionMode = SelectionMode.Single,
                state = verseListState,
                onSelectedIndexesChange = { selectedIndexes ->
                    selectedIndexes.firstOrNull()?.let { index ->
                        currentVerse = verses[index]
                    }
                },
                contentPadding = PaddingValues(8.dp),
                content = {
                    items(
                        count = verses.size,
                        key = { index -> "chapter_${currentChapter}_verse_${verses[index]}" }
                    ) { index ->
                        val verseNumber = verses[index]
                        val isSelected = verseNumber == currentVerse

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) JewelTheme.globalColors.outlines.focused else Color.Transparent
                                )
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "פסוק ${verseNumber.toHebrewNumeral()}",
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            )
        }

        // Right column to display the selected verse
        Column(
            modifier = Modifier
                .weight(3f)
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            currentVerseData?.let { verse ->
                VerseScreen(verse)
            } ?: run {
                Text("טעינה את הפסוק...")
            }
        }
    }
}

// Example implementation of loadVerse
fun loadVerse(bookTitle: String, chapter: Int, verse: Int): Verse {
    val path = "../database/$GENERATED_FOLDER/$bookTitle/$chapter/$verse.json"
    val file = File(path)
    val verseJson = file.readText()
    return Json.decodeFromString<Verse>(verseJson)
}

@Composable
fun VerseScreen(verse: Verse) {
    val fontAwesome = FontFamily(org.jetbrains.compose.resources.Font(Res.font.`Guttman David_Bold`))

    LazyColumn(
        modifier = Modifier.padding(16.dp)
    ) {
        item {
            Text(
                text = remember(verse.text) { htmlToAnnotatedString(verse.text) },
                fontSize = 18.sp,
                textAlign = TextAlign.Justify,
                fontFamily = fontAwesome,
                lineHeight = 22.sp
            )
        }

        // Internal function to display a block of commentaries
        fun <T : CommentaryBase> displayCommentSection(
            title: String,
            commentList: List<T>
        ) {
            if (commentList.isNotEmpty()) {
                // Display the section title
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                }
                // Display each commentary in this section
                commentList.forEach { commentary ->
                    item {
                        Text("— ${commentary.commentatorName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        commentary.texts.forEach { text ->
                            Text(
                                text = remember(text) { htmlToAnnotatedString(text) },
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Justify,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }

        // Display different categories of commentaries
        displayCommentSection("פירושים", verse.commentary)
        displayCommentSection("פירושים צדדים", verse.quotingCommentary)
        displayCommentSection("מקורות", verse.reference)
        displayCommentSection("קישורים אחרים", verse.otherLinks)
    }
}
