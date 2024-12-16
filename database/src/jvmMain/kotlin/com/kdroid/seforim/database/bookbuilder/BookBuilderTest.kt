package com.kdroid.seforim.database.bookbuilder

import com.kdroid.seforim.database.bookbuilder.util.buildBookFromShape
import com.kdroid.seforim.database.bookbuilder.util.logger

suspend fun main() {
    val bookTitle = "Mishnah Berakhot"
    logger.info("Starting to build book: $bookTitle")
    buildBookFromShape(bookTitle)
}
