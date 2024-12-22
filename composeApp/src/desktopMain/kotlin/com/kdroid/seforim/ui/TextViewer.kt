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
import com.kdroid.gematria.converter.toDafGemara
import com.kdroid.gematria.converter.toHebrewNumeral
import com.kdroid.seforim.constants.GENERATED_FOLDER
import com.kdroid.seforim.core.model.BookIndex
import com.kdroid.seforim.core.model.BookType
import com.kdroid.seforim.core.model.CommentaryBase
import com.kdroid.seforim.core.model.Verse
import kotlinx.io.IOException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
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
    val json = Json {
        ignoreUnknownKeys = true
    }
    return json.decodeFromString<BookIndex>(indexJson)
}


@Composable
fun BookViewScreen(bookIndex: BookIndex) {
    val defaultChapter = 1
    val defaultVerse = 1
    var currentChapter by remember { mutableStateOf(defaultChapter) }
    var currentVerse by remember { mutableStateOf(defaultVerse) }

    // Get current chapter's offset
    val currentChapterOffset = remember(currentChapter) {
        bookIndex.chapters.find { it.chapterNumber == currentChapter }?.offset ?: 0
    }

    // Load data for the current verse, taking offset into account
    val currentVerseData by produceState<Verse?>(initialValue = null, currentChapter, currentVerse) {
        value = loadVerse(bookIndex.title, currentChapter, currentVerse + currentChapterOffset)
    }

    val chapters = remember(bookIndex) {
        bookIndex.chapters
            .map { it.chapterNumber }
            .filter { it >= 0 }
    }

//     List of verses for the selected chapter, adjusted for offset
    val verses = remember(currentChapter, bookIndex) {
        bookIndex.chapters
            .firstOrNull { it.chapterNumber == currentChapter }
            ?.let { chapter ->
                (1..chapter.numberOfVerses).map { it }
            }
            ?: emptyList()
    }

    val chapterListState = rememberSelectableLazyListState()
    val verseListState = rememberSelectableLazyListState()

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
        ) {
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
                            if (bookIndex.sectionNames.isEmpty())
                                Text(chapterNumber.toHebrewNumeral(), fontSize = 10.sp)
                            else
                                Text(
                                    text = "${bookIndex.sectionNames[0]} ${if(bookIndex.type == BookType.TALMUD) chapterNumber.toDafGemara() else chapterNumber.toHebrewNumeral()}",
                                    fontSize = 12.sp
                                )
                        }
                    }
                }
            )

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
                            if (bookIndex.sectionNames.isEmpty())
                                Text(verseNumber.toHebrewNumeral(), fontSize = 10.sp)
                            else {
                                // Display the actual verse number (with offset) in the UI
                                val displayVerseNumber = verseNumber + currentChapterOffset
                                Text(
                                    text = "${bookIndex.sectionNames[1]} ${displayVerseNumber.toHebrewNumeral()}",
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            )
        }

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

fun loadVerse(bookTitle: String, chapter: Int, verse: Int): Verse? {
    val path = "../database/$GENERATED_FOLDER/$bookTitle/$chapter/$verse.json"
    val file = File(path)

    return try {
        val verseJson = file.readText()
        Json.decodeFromString<Verse>(verseJson)
    } catch (e: IOException) {
        println("Error reading file at $path: ${e.message}")
        null
    } catch (e: SerializationException) {
        println("Error parsing JSON for Verse at $path: ${e.message}")
        null
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal fun loadVerseFromProto(bookTitle: String, chapter: Int, verse: Int): Verse? {
    val path = "../database/$GENERATED_FOLDER/$bookTitle/$chapter/$verse.proto"
    val file = File(path)

    return try {
        val protoData = file.readBytes() // Lit les données binaires du fichier
        ProtoBuf.decodeFromByteArray<Verse>(protoData) // Désérialise les données Protobuf en objet `Verse`
    } catch (e: IOException) {
        println("Error reading file at $path: ${e.message}")
        null
    } catch (e: SerializationException) {
        println("Error parsing Protobuf for Verse at $path: ${e.message}")
        null
    }
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
                        Text("— ${commentary.commentator.name}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        commentary.texts.forEach { text ->
                            Text(
                                text = remember(text) { htmlToAnnotatedString(text.text) },
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
        displayCommentSection("תרגומים", verse.targum)
        displayCommentSection("פירושים", verse.commentary)
        displayCommentSection("פירושים צדדים", verse.quotingCommentary)
        displayCommentSection("מקורות", verse.reference)
        displayCommentSection("קישורים אחרים", verse.otherLinks)
    }
}
