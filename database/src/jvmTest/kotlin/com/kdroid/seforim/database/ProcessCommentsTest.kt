package com.kdroid.seforim.database

import com.kdroid.seforim.database.builders.sefaria.book.model.CollectiveTitle
import com.kdroid.seforim.database.builders.sefaria.book.model.CommentItem
import com.kdroid.seforim.database.builders.sefaria.book.util.processComments
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.annotations.TestOnly

@TestOnly
fun testProcessComments() {
    val commentsList = listOf(
        CommentItem(
            _id = "1",
            index_title = "Rashi on Horayot",
            category = "commentary",
            type = null,
            ref = "",
            anchorRef = "",
            anchorRefExpanded = emptyList(),
            sourceRef = "Rashi on Horayot 2a:20:1",
            sourceHeRef = "",
            anchorVerse = 0,
            sourceHasEn = false,
            compDate = null,
            commentaryNum = 0.0,
            collectiveTitle = CollectiveTitle(he = "Rashi", en = "Rashi"),
            heTitle = null,
            he = JsonPrimitive("Rashi Text"),
            text = JsonPrimitive("Rashi English Text")
        ),
        CommentItem(
            _id = "2",
            index_title = "Mishneh Torah, Rebels",
            category = "commentary",
            type = null,
            ref = "",
            anchorRef = "",
            anchorRefExpanded = emptyList(),
            sourceRef = "Mishneh Torah, Rebels 3:6",
            sourceHeRef = "",
            anchorVerse = 0,
            sourceHasEn = false,
            compDate = null,
            commentaryNum = 0.0,
            collectiveTitle = CollectiveTitle(he = "Mishneh Torah", en = "Mishneh Torah"),
            heTitle = null,
            he = JsonPrimitive("Mishneh Torah Text"),
            text = JsonPrimitive("Mishneh Torah English Text")
        ),
        CommentItem(
            _id = "3",
            index_title = "Reshimot Shiurim on Horayot",
            category = "commentary",
            type = null,
            ref = "",
            anchorRef = "",
            anchorRefExpanded = emptyList(),
            sourceRef = "Reshimot Shiurim on Horayot 2a:9-22",
            sourceHeRef = "",
            anchorVerse = 0,
            sourceHasEn = false,
            compDate = null,
            commentaryNum = 0.0,
            collectiveTitle = CollectiveTitle(he = "Reshimot Shiurim", en = "Reshimot Shiurim"),
            heTitle = null,
            he = JsonPrimitive("Reshimot Shiurim Text"),
            text = JsonPrimitive("Reshimot Shiurim English Text")
        ),
        CommentItem(
            _id = "4",
            index_title = "Reshimot Shiurim on Horayot",
            category = "commentary",
            type = null,
            ref = "",
            anchorRef = "",
            anchorRefExpanded = emptyList(),
            sourceRef = "Reshimot Shiurim on Horayot 2a:36-42",
            sourceHeRef = "",
            anchorVerse = 0,
            sourceHasEn = false,
            compDate = null,
            commentaryNum = 0.0,
            collectiveTitle = CollectiveTitle(he = "Reshimot Shiurim", en =  "Reshimot Shiurim"),
            heTitle = null,
            he = JsonPrimitive("Reshimot Shiurim Text 2"),
            text = JsonPrimitive("Reshimot Shiurim English Text 2")
        )
    )

    val processed = processComments(commentsList)

    processed.forEach { println(it) }
}
