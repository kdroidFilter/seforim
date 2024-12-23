package com.kdroid.seforim.database.common

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kdroid.seforim.core.model.Verse
import com.kdroid.seforim.database.Database
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf

fun createDatabase(): Database {
    val currentDirectory = System.getProperty("user.dir") // Répertoire actuel
    val databasePath = "$currentDirectory/seforim.db" // Chemin vers la base de données
    val driver = JdbcSqliteDriver("jdbc:sqlite:$databasePath")
    Database.Schema.create(driver) // Crée la base de données si elle n'existe pas
    println("Base de données initialisée à l'emplacement : $databasePath")
    return Database(driver)
}


@OptIn(ExperimentalSerializationApi::class)
fun insertVerse(book: String, chapter: Int, verse: Int, verseData: Verse) {
    val database = createDatabase()
    val protoData = ProtoBuf.encodeToByteArray(verseData)
    database.versesQueries.insertVerse(
        book = book,
        chapter = chapter.toLong(),
        verse = verse.toLong(),
        data_ = protoData
    )
}
