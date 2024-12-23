package com.kdroid.seforim.database.builders.sefaria.book

import com.kdroid.seforim.constants.GENERATED_FOLDER
import com.kdroid.seforim.database.builders.sefaria.book.util.buildBookFromShape
import com.kdroid.seforim.database.builders.sefaria.book.util.logger
import com.kdroid.seforim.database.common.createDatabase

suspend fun main() {
    val bookTitle = "Amos"
    logger.info("Starting to build book: $bookTitle")
    val database = createDatabase()
    buildBookFromShape(bookTitle, GENERATED_FOLDER, database)
}
