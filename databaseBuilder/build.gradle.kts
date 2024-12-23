plugins {
    application
    alias(libs.plugins.kotlinMultiplatform)
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

kotlin {
    jvm()
}

application {
    mainClass.set("com.kdroid.seforim.database.builders.sefaria.BuildKt")
}

dependencies {
    implementation(project(":database"))
}

tasks {
    // Tâche pour créer un Fat JAR
    withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
        archiveClassifier.set("all") // Nom du fichier JAR
    }
}