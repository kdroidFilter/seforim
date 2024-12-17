package com.kdroid.seforim.database.builders.book

import com.kdroid.seforim.database.builders.book.util.buildBookFromShape
import com.kdroid.seforim.database.builders.book.util.logger
import com.kdroid.seforim.database.common.constants.GENERATED_FOLDER

suspend fun main() {
    val bookTitle = "Tur"
    logger.info("Starting to build book: $bookTitle")
    buildBookFromShape(bookTitle, GENERATED_FOLDER)
}
