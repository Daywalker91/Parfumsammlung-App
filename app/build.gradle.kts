import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Version/Tag-Synchronisierung mit der CI/CD-Pipeline (siehe Parfum-App_CICD_Plan.md):
// versionCode/versionName werden im CI-Build per -PversionCode/-PversionName aus
// github.run_number gesetzt. Lokale Dev-Builds ohne diese Properties bekommen
// sinnvolle Defaults.
val ciVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val ciVersionName = project.findProperty("versionName") as String? ?: "0.0-dev"

// Release-Signierung: direkt in Gradle statt über eine Drittanbieter-Action
// (r0adkll/sign-android-release), die auf dem GitHub-Runner eine feste,
// dort nicht vorhandene Build-Tools-Version (29.0.3) erwartete. So nutzt
// die Signierung dieselbe Build-Tools-Version, die dieses Projekt ohnehin
// schon zieht — und assembleRelease liefert direkt eine fertig signierte
// APK, kein separater Signing-Schritt nötig. Lokale Builds ohne diese
// Properties bleiben unsigniert (unverändertes Verhalten).
val signingStoreFile = project.findProperty("signingStoreFile") as String?
val signingStorePassword = project.findProperty("signingStorePassword") as String?
val signingKeyAlias = project.findProperty("signingKeyAlias") as String?
val signingKeyPassword = project.findProperty("signingKeyPassword") as String?
val hasReleaseSigning = signingStoreFile != null

android {
    namespace = "com.daywalker91.parfumsammlung"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.daywalker91.parfumsammlung"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = ciVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(signingStoreFile!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Für BuildConfig.VERSION_CODE/VERSION_NAME im Self-Update-Mechanismus
        // (Vergleich mit dem neuesten GitHub Release) — seit AGP 8 opt-in.
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Bildanzeige (Phase 2)
    implementation(libs.coil.compose)
    // EXIF-Auslesung (Bild-Rotation von Kamerafotos korrigieren)
    implementation(libs.androidx.exifinterface)

    // Verschlüsselte Ablage des Gemini-API-Keys (Phase 4)
    implementation(libs.androidx.security.crypto)

    // On-device Barcode-Scan, kein CameraX/eigene Kamera-UI nötig (Phase 3)
    implementation(libs.play.services.code.scanner)

    // Gemini-REST-Calls (Phase 4) — bewusst kein Google-AI-SDK, um keine weitere
    // Dependency-Versionsfront neben AGP/Kotlin/Compose aufzumachen.
    implementation(libs.okhttp)
}
