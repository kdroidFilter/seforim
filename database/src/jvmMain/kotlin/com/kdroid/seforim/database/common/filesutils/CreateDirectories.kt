package com.kdroid.seforim.database.common.filesutils

import java.io.File

fun createDirectoriesIfNotExist() {
    // Obtenir le répertoire actuel
    val currentDir = File(System.getProperty("user.dir"))
    // Définir le chemin du dossier `generated`
    val generatedDir = File(currentDir, "generated")
    // Définir le chemin du dossier `db` à l'intérieur de `generated`
    val dbDir = File(generatedDir, "db")

    // Vérifier et créer le dossier `generated` s'il n'existe pas
    if (!generatedDir.exists()) {
        if (generatedDir.mkdirs()) {
            println("Dossier 'generated' créé avec succès dans le répertoire actuel.")
        } else {
            println("Erreur lors de la création du dossier 'generated'.")
        }
    } else {
        println("Dossier 'generated' existe déjà.")
    }

    // Vérifier et créer le dossier `db` s'il n'existe pas
    if (!dbDir.exists()) {
        if (dbDir.mkdirs()) {
            println("Dossier 'db' créé avec succès dans le dossier 'generated'.")
        } else {
            println("Erreur lors de la création du dossier 'db'.")
        }
    } else {
        println("Dossier 'db' existe déjà.")
    }
}