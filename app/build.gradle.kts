import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Version is injected by the release workflow via -PversionName=X.Y.Z (from the git tag).
// Local and CI debug builds fall back to a dev version. See docs/RELEASING.md.
val appVersionName: String = providers.gradleProperty("versionName").orNull ?: "0.0.0-dev"

fun versionCodeFor(versionName: String): Int {
    val match = Regex("""^(\d+)\.(\d+)\.(\d+)""").find(versionName) ?: return 1
    val (major, minor, patch) = match.destructured
    // coerceAtLeast: "0.0.0-dev" would otherwise compute 0, which AGP rejects.
    return (major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()).coerceAtLeast(1)
}

android {
    namespace = "app.dift"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.dift"
        minSdk = 35 // single-device app (Fairphone 6, Android 15+) — no backward-compat tax
        targetSdk = 36
        versionCode = versionCodeFor(appVersionName)
        versionName = appVersionName
    }

    signingConfigs {
        // Populated from env vars in the release workflow; absent locally.
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_FILE")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Minification is deliberately off: APK size is irrelevant for a personal app and
            // R8 config drift is a classic silent breaker. Do not enable without an ADR.
            isMinifyEnabled = false
            signingConfig = if (System.getenv("KEYSTORE_FILE") != null) {
                signingConfigs.getByName("release")
            } else {
                // Fallback so `assembleRelease` always works locally / for agents.
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // CI runs :app:lint on every push; warnings stay visible but only errors fail the build.
        abortOnError = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    jvmToolchain(21)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("detekt.yml"))
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // Explicit: OverlayComposeHost needs the setViewTree* extensions at compile time.
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.konsist)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
}
