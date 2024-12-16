package com.kdroid.seforim.database.builders.tableofcontent.api

import com.kdroid.seforim.core.model.TableOfContent
import com.kdroid.seforim.database.common.constants.BASE_URL
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

internal suspend fun fetchTableOfContents(): List<TableOfContent> {
    val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    return client.get("$BASE_URL/index/").body()
}