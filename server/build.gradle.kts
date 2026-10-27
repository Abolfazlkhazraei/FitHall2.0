// FitHall API server: holds the Anthropic API key and proxies the app's AI requests,
// with per-device rate limits and a global daily budget.
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
    application
}

group = "com.fithall.app"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.fithall.app.server.ApplicationKt")
}

ktor {
    fatJar {
        archiveFileName.set("fithall-server.jar")
    }
}

// The privacy policy and terms are written in docs/ and served as web pages by the server.
tasks.processResources {
    from(rootProject.file("docs")) {
        include("privacy-policy.md", "terms.md")
        into("pages")
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.anthropic.java)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serverStatusPages)
    implementation(libs.ktor.serverCallLogging)
    implementation(libs.ktor.serverForwardedHeader)
    implementation(libs.ktor.serializationJson)
    implementation(libs.kotlinx.serializationJson)
    implementation(libs.logback)
    implementation(libs.googleAuth)
    implementation(libs.commonmark)
    implementation(libs.commonmark.tables)

    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.ktor.clientContentNegotiation)
    testImplementation(libs.kotlin.testJunit)
}
