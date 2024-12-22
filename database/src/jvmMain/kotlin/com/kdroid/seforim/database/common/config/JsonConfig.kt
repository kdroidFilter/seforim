package com.kdroid.seforim.database.common.config

import kotlinx.serialization.json.Json

// Configure Json with ignoreUnknownKeys
internal val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true // Ignore unknown keys in JSON
    isLenient = true
}