package com.kdroid.seforim.database.builders.directories.api

import com.kdroid.seforim.database.common.constants.BASE_URL
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

suspend fun fetchJsonFromApi(title: String, endpoint: String): String {
    val client = HttpClient(CIO)
    return try {
        val response: HttpResponse = client.get("$BASE_URL/$endpoint/${title}") {
            header("accept", "application/json")
        }
        client.close()
        response.bodyAsText()
    } catch (e: Exception) {
        client.close()
        "{\"error\": \"Failed to fetch data for $title\"}"
    }
}