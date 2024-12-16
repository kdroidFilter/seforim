package com.kdroid.seforim.database.builders.book.api

import kotlinx.serialization.json.Json

// Configure Json with ignoreUnknownKeys
internal val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true // Ignore unknown keys in JSON
}