plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.protobuf)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.slf4j.simple)
            implementation(libs.zstd.jni)

            implementation(libs.hebrewnumerals)

        }
    }
}
