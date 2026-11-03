plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

// Keep generated Java argument files and class paths ASCII-only on Windows/JDK 17.
System.getenv("JIZHANG_BUILD_ROOT")?.let { outputRoot ->
    allprojects { layout.buildDirectory.set(file("$outputRoot/${project.name}")) }
}
