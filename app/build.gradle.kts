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
        versionCode = 14
        versionName = "4.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx.v1120)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx.v270)
    implementation(libs.androidx.recyclerview)
    implementation(libs.jsoup)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.core.ktx.v1131)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.osmdroid.android)
    // Gson für JSON Serialisierung (Caching)
    implementation(libs.gson)
    implementation(libs.material.v1110)
    implementation("io.coil-kt:coil:2.5.0")
    // YouTube Player
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.0")
}
