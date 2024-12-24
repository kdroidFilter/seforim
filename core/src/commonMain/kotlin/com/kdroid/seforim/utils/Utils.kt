package com.kdroid.seforim.utils

import java.io.File
import java.io.FileWriter
import java.io.IOException

// 2.1 Supprimer les signes diacritiques en hébreu (nikoud)
fun String.removeHebrewNikud(): String {
    val hebrewVowelRegex = Regex("[\\u0591-\\u05C7]")
    return hebrewVowelRegex.replace(this, "")
}

// 2.2 Conversion Int => Format "2a", "3b", etc.
fun Int.toGuemaraInt(): String {
    val numberPart = (this + 1) / 2 + 1
    val suffix = if (this % 2 == 1) "a" else "b"
    return "$numberPart$suffix"
}

// 2.3 Conversion String ("3a") => Int
fun String.toGuemaraNumber(): Int? {
    // Valider que la chaîne correspond à un format attendu comme "3a" ou "4b"
    val regex = """(\d+)([ab])""".toRegex()
    val matchResult = regex.matchEntire(this) ?: return null

    val numberPart = matchResult.groupValues[1].toInt() // La partie numérique
    val suffix = matchResult.groupValues[2]            // La partie lettre

    return when (suffix) {
        "a" -> (numberPart - 1) * 2 + 1
        "b" -> (numberPart - 1) * 2
        else -> null // En cas de suffixe invalide
    }
}

// 2.4 Normaliser les références "exotiques" en un format plus simple
fun String.normalizeRef(): String {
    // Cas 0 : Détection d’un format Talmudique ("2a:3", "4b:17:3", etc.)
    // On ne le transforme pas, mais on s'assure de ne garder que deux segments
    if (this.matches("""\d+[ab](?::\d+){1,2}""".toRegex())) {
        val segments = this.split(":")
        return if (segments.size > 2) {
            "${segments[0]}:${segments[1]}"
        } else {
            this
        }
    }

    return when {
        // Cas 1 : Format "4:27-28" => on garde "4:27"
        this.matches("""\d+:\d+-\d+""".toRegex()) -> {
            this.substringBefore("-")
        }

        // Cas 2 : Formats avec des mots et chiffres, y compris les virgules
        // Exemple : "Leviticus 6:2", "Mishneh Torah, Rebels 3:6" => "6:2", "3:6"
        this.contains(":") -> {
            // Utiliser une regex pour extraire le premier pattern "number:number"
            val match = Regex("""(\d+):(\d+)""").find(this)
            match?.let { "${it.groupValues[1]}:${it.groupValues[2]}" } ?: "$this:1"
        }

        // Cas 3 : Texte en hébreu ou autre + nombre sans deux segments
        // Exemple : "יְדַע 1," => "1:1"
        this.matches(""".*?\d+(,\s*.*)?""".toRegex()) -> {
            val digit = this.replace(Regex("[^\\d]"), "") // Extraire juste la partie numérique
            "$digit:1"
        }

        // Cas 4 : Format "Positive Commandments 217" => "217:1"
        this.matches(""".*\b\d+\b.*""".toRegex()) -> {
            val digit = this.replace(Regex("[^\\d]"), "")
            "$digit:1"
        }

        // Par défaut, on retourne la chaîne telle quelle
        else -> this
    }
}

// 2.5 Enlever le dernier segment ":xxx" s'il y a plus de 2 segments
fun String.removeLastSegment(): String {
    val parts = this.split(":")
    return if (parts.size > 2) {
        parts.dropLast(1).joinToString(":")
    } else {
        this
    }
}

// 2.6 Extraire le format "2a" ou "10b" s’il existe dans la chaîne
fun String.extractNumberAndLetterAsString(): String? {
    val regex = """(\d+)([ab])""".toRegex()
    val matchResult = regex.find(this)
    return matchResult?.let {
        val number = it.groupValues[1]
        val letter = it.groupValues[2]
        "$number$letter"
    }
}



fun logToFile(fileName: String, message: String) {
    try {
        val logFile = File(fileName)

        if (!logFile.exists()) {
            logFile.createNewFile()
        }

        FileWriter(logFile, true).use { writer ->
            writer.write("${System.currentTimeMillis()} - $message\n")
        }
    } catch (e: IOException) {
        println("Erreur lors de l'écriture dans le fichier : ${e.message}")
    }
}