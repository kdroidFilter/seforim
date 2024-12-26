package com.kdroid.seforim.database.builders.sefaria.book.api

import com.kdroid.seforim.database.builders.sefaria.book.util.logger
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

/**
 * Fetches JSON data from the specified API URL. This method sends an HTTP
 * GET request to the provided URL and expects a JSON response. If the
 * response is not in JSON format, it returns an empty JSON object. In case
 * of an exception, the method retries the request.
 *
 * @param url The API endpoint from which the JSON data will be fetched.
 * @return A String containing the JSON response from the API. If the
 *    response is not valid JSON, an empty JSON object ("{}") is returned.
 */
private var repetitions = 0

internal suspend fun fetchJsonFromApi(url: String): String {
    val client = HttpClient(CIO)
    logger.info("Fetching data from: $url")
    return try {
        val response: HttpResponse = client.get(url) {
            header("accept", "application/json")
        }
        val body = response.bodyAsText()
        logger.debug("Response received from $url: ${body.take(100)}...")
        if (body.trim().startsWith("{") || body.trim().startsWith("[")) {
            body
        } else {
            logger.warn("Non-JSON response from $url")
            "{}" // Returns an empty JSON to avoid crashing
        }
    } catch (e: Exception) {
        logger.error("Error fetching data from $url: ${e.message}", e)
        if (repetitions < 5) {
            repetitions++
            fetchJsonFromApi(url) // Retries the request in case of an error

        } else "{}"
    } finally {
        client.close()
    }
}