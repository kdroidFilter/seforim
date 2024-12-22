package com.kdroid.seforim.database.builders.sefaria.book.util

private val traductionMap = mapOf(
    "Pasuk" to "פסוק",
    "Daf" to "דף",
    "Line" to "שורה",
    "Paragraph" to "סעיף",
    "Chapter" to "פרק",
    "Verse" to "פסוק",
    "Siman" to "סימן",
    "Seif" to "סעיף"
)

internal fun translateSection(input: String): String {
    return traductionMap[input] ?: input
}

internal fun translateSections(inputs: List<String>): List<String> {
    return inputs.map { input -> translateSection(input) }
}
