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
            implementation("io.ktor:ktor-client-core:3.0.2")
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation("io.ktor:ktor-serialization-kotlinx-protobuf:3.0.2")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.2")
            implementation(libs.slf4j.simple)
            implementation(libs.jewel.foundation)
        }
    }
}
