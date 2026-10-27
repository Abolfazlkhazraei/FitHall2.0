import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

// Firebase Crashlytics needs google-services.json from your Firebase project (Project settings ->
// your Android app). Without it the app still builds, with crash reporting off; release builds
// require it (see checkReleaseConfig). The file is git-ignored.
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// The app talks only to the FitHall server, which holds the Anthropic key. Set BACKEND_URL in
// local.properties; the default reaches a server running on your computer from the Android emulator.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val backendUrl: String = localProperties.getProperty("BACKEND_URL") ?: "http://10.0.2.2:8080"
// Google Cloud project linked to the app in the Play Console; enables Play Integrity. 0 = off.
val playCloudProjectNumber: String = localProperties.getProperty("PLAY_CLOUD_PROJECT_NUMBER")?.trim()?.ifEmpty { null } ?: "0"

// Release settings come from local.properties, or environment variables of the same name on CI.
// They're checked before any release build starts (see checkReleaseConfig below).
fun releaseSetting(name: String): String? =
    localProperties.getProperty(name)?.trim()?.ifEmpty { null } ?: System.getenv(name)?.trim()?.ifEmpty { null }

val releaseBackendUrl = releaseSetting("RELEASE_BACKEND_URL")
// Optional: the server publishes docs/privacy-policy.md at <backend>/privacy, which the app links to
// by default. Set this only to host the policy somewhere else.
val privacyPolicyUrl = releaseSetting("PRIVACY_POLICY_URL")
val releaseStoreFile = releaseSetting("RELEASE_STORE_FILE")?.let { rootProject.file(it) }
val releaseStorePassword = releaseSetting("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSetting("RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSetting("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = releaseStoreFile != null && releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null

val releaseProblems: List<String> = buildList {
    if (releaseBackendUrl == null) add("RELEASE_BACKEND_URL is not set (your deployed server, e.g. https://api.fithall.app)")
    else if (!releaseBackendUrl.startsWith("https://")) add("RELEASE_BACKEND_URL must start with https:// (got $releaseBackendUrl)")
    if (privacyPolicyUrl != null && !privacyPolicyUrl.startsWith("https://")) add("PRIVACY_POLICY_URL must start with https:// (got $privacyPolicyUrl)")
    if (!hasFirebaseConfig) add("composeApp/google-services.json is missing (download it from your Firebase project; accounts, crash reporting and analytics need it)")
    if (!hasReleaseSigning) add("Signing is not configured: set RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS and RELEASE_KEY_PASSWORD")
    else if (releaseStoreFile?.exists() != true) add("RELEASE_STORE_FILE not found: $releaseStoreFile")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    jvm()
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.okhttp)
            implementation(libs.ktor.clientOkhttp)
            implementation(libs.sqldelight.androidDriver)
            implementation(libs.androidx.workRuntime)
            implementation(libs.androidx.splashscreen)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.crashlytics)
            implementation(libs.firebase.analytics)
            implementation(libs.playServices.codeScanner)
            implementation(libs.playIntegrity)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.kotlinx.datetime)
            implementation(projects.shared)
            implementation(libs.ktor.clientCore)
            implementation(libs.ktor.clientContentNegotiation)
            implementation(libs.ktor.serializationJson)
            implementation(libs.sqldelight.runtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.ktor.clientMock)
        }
        iosMain.dependencies {
            implementation(libs.ktor.clientDarwin)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.clientOkhttp)
        }
        jvmTest.dependencies {
            implementation(libs.sqldelight.sqliteDriver)
        }
    }
}

android {
    namespace = "com.fithall.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.fithall.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Bump VERSION_CODE for every upload to Google Play.
        versionCode = releaseSetting("VERSION_CODE")?.toInt() ?: 1
        versionName = releaseSetting("VERSION_NAME") ?: "1.0"
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
        buildConfigField("long", "PLAY_CLOUD_PROJECT_NUMBER", "${playCloudProjectNumber}L")
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${privacyPolicyUrl.orEmpty()}\"")
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/{DEPENDENCIES,LICENSE*,NOTICE*,INDEX.LIST,versions/9/previous-compilation-data.bin}"
        }
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("String", "BACKEND_URL", "\"${releaseBackendUrl.orEmpty()}\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

// Fails a release build up front, with every problem listed, instead of producing an app that
// points at the emulator or can't be uploaded.
val checkReleaseConfig by tasks.registering {
    // A plain String, so the configuration cache can store it.
    val problems: String = releaseProblems.joinToString("\n") { "  - $it" }
    doLast {
        if (problems.isNotEmpty()) {
            throw GradleException("Release build is not configured:\n$problems\nSee README > Release builds.")
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseConfig) }

// History (meals, workouts, weigh-ins, check-ins, chat) lives in SQLite; tables are defined in
// src/commonMain/sqldelight. Once version 1 has shipped, any table change needs a numbered .sqm
// migration (see https://sqldelight.github.io/sqldelight/latest/multiplatform_sqlite/migrations/).
sqldelight {
    databases {
        create("FitHallDatabase") {
            packageName.set("com.fithall.app.db")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.fithall.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.fithall.app"
            packageVersion = "1.0.0"
        }
    }
}
