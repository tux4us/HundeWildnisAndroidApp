import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
}

android {
    namespace = "com.hundewildnis.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hundewildnis.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 66
        versionName = "12.1"

        // API Key aus local.properties laden
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
            isMinifyEnabled = true
            isShrinkResources = true
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

    kotlinOptions {
        jvmTarget = "17"
    }

    kotlin {
        jvmToolchain(17)
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
    
    // Google Play Services & YouTube Player entfernt für F-Droid Kompatibilität

    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.github.bumptech.glide:glide:5.0.5")

    // ✅ HUNDE-STAMMDATEN
    implementation("com.google.zxing:core:3.5.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.itextpdf:itext7-core:7.2.5")
    implementation("com.google.code.gson:gson:2.10.1")

    //PhotoPicker für F-Droid Kompatibilität und PlayStore Konformität
    implementation("androidx.activity:activity-ktx:1.8.2")
}