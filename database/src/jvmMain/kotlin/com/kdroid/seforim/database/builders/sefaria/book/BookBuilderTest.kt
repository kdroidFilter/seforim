package com.kdroid.seforim.database.builders.sefaria.book

import com.kdroid.seforim.constants.GENERATED_FOLDER
import com.kdroid.seforim.database.builders.sefaria.book.util.buildBookFromShape
import com.kdroid.seforim.database.builders.sefaria.book.util.logger
import com.kdroid.seforim.database.common.createDatabase

suspend fun main() {
    val bookList = listOf("Rashi on Horayot", "Horayot", "Amos", "Tur")
    val bookTitle = "Shabbat"
    logger.info("Starting to build book: $bookTitle")
    val database = createDatabase()
//    for (book in bookList) {
//        buildBookFromShape(book, GENERATED_FOLDER, database)
//    }
    buildBookFromShape(bookTitle, GENERATED_FOLDER, database)
}
