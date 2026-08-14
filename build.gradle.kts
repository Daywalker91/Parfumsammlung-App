// Root build file — plugin versions are declared here (applied `false`) and
// resolved per-module in app/build.gradle.kts. Keeps a single source of truth
// for AGP/Kotlin/KSP versions across the (currently single) app module.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
