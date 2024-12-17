package com.kdroid.seforim.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import be.digitalia.compose.htmlconverter.htmlToAnnotatedString
import com.kdroid.seforim.core.model.BookIndex
import com.kdroid.seforim.core.model.CommentaryBase
import com.kdroid.seforim.core.model.Verse
import kotlinx.serialization.json.Json
import org.jetbrains.jewel.ui.component.Text
import seforim.composeapp.generated.resources.`Guttman David_Bold`
import seforim.composeapp.generated.resources.Res
import java.io.File

fun loadVerse(): Verse {
    val path = "/home/elyahou/IdeaProjects/seforim/database/generated/Obadiah/1/1.json"
    val file = File(path)
    val verseJson = file.readText()
    return Json.decodeFromString<Verse>(verseJson)
}

@Composable
fun BookIndexScreen(bookIndex: BookIndex) {
    // Affiche le titre, le nombre de chapitres, etc.
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Text("Titre: ${bookIndex.title}")
        Text("Hébreu: ${bookIndex.heTitle}")
        Text("Chapitres: ${bookIndex.numberOfChapters}")

        bookIndex.chapters.forEach { chapterIndex ->
            Text("Chapitre ${chapterIndex.chapterNumber}: ${chapterIndex.numberOfVerses} versets")
            if (chapterIndex.commentators.isNotEmpty()) {
                Text("Commentateurs : ${chapterIndex.commentators.joinToString(", ")}")
            }
        }
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
                verse.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontFamily = fontAwesome,
                lineHeight = 22.sp
            )
        }

        // Titre général des commentaires
        item {
            Spacer(Modifier.height(16.dp))
            Text("פירושים :", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
        }

        // Fonction interne pour factoriser l’affichage d’un bloc de commentaires
        fun <T : CommentaryBase> displayCommentSection(
            title: String,
            commentList: List<T>
        ) {
            if (commentList.isNotEmpty()) {
                // Affiche le titre de la section
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                }
                // Affiche chaque commentaire de cette section
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

        // Affichage des différentes catégories de commentaires
        displayCommentSection("Commentary", verse.commentary)
        displayCommentSection("Quoting Commentary", verse.quotingCommentary)
        displayCommentSection("Reference", verse.reference)
        displayCommentSection("Other Links", verse.otherLinks)
    }
}
