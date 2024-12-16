package com.kdroid.seforim.database.bookbuilder.api

import kotlinx.serialization.json.Json

// Configuration de Json avec ignoreUnknownKeys
internal val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true // Ignore les clés inconnues dans le JSON
}
