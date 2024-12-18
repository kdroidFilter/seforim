package com.kdroid.seforim.database.builders.book

import com.kdroid.seforim.constants.GENERATED_FOLDER
import com.kdroid.seforim.database.builders.book.util.buildBookFromShape
import com.kdroid.seforim.database.builders.book.util.logger

suspend fun main() {
    val bookTitle = "Beit Yosef"
    logger.info("Starting to build book: $bookTitle")
    buildBookFromShape(bookTitle, GENERATED_FOLDER)
}
