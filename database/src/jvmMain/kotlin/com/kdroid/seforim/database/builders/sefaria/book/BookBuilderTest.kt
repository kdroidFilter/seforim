package com.kdroid.seforim.database.builders.sefaria.book

import com.kdroid.seforim.constants.GENERATED_FOLDER
import com.kdroid.seforim.database.builders.sefaria.book.util.buildBookFromShape
import com.kdroid.seforim.database.builders.sefaria.book.util.logger

suspend fun main() {
    val bookTitle = "Horayot"
    logger.info("Starting to build book: $bookTitle")
    buildBookFromShape(bookTitle, GENERATED_FOLDER)
}
