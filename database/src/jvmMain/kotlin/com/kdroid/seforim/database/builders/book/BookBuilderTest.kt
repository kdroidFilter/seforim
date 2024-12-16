package com.kdroid.seforim.database.builders.book

import com.kdroid.seforim.database.builders.book.util.buildBookFromShape
import com.kdroid.seforim.database.builders.book.util.logger

suspend fun main() {
    val bookTitle = "Obadiah"
    logger.info("Starting to build book: $bookTitle")
    buildBookFromShape(bookTitle)
}
