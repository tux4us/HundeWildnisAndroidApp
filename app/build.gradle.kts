import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hundewildnis.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hundewildnis.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 39
        versionName = "8.0"

        // API Key aus local.properties laden - AUSSERHALB von android {}
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }

        val youtubeApiKey = localProperties.getProperty("YOUTUBE_API_KEY", "")
        buildConfigField("String", "YOUTUBE_API_KEY", "\"$youtubeApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true        // ← Code Obfuscation
            isShrinkResources = true      // ← Ressourcen optimieren
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.jsoup)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.osmdroid.android)
    implementation(libs.gson)
    implementation(libs.coil)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.youtube.player.core)

    // OPTIONAL: Für bessere Foto-Anzeige
// Füge in build.gradle.kts (Module: app) unter dependencies ein:

    implementation("com.github.bumptech.glide:glide:5.0.5")

// Falls Glide nicht verfügbar: PhotoAdapter funktioniert auch ohne!
// Der Adapter hat einen Fallback auf setImageURI()

}
