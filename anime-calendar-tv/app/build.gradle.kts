plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.peden88.animecalendar.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.peden88.animecalendar.tv"
        minSdk = 24
        targetSdk = 35
        versionCode = (System.getenv("ANIME_TV_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("ANIME_TV_VERSION_NAME") ?: "1.0.0-tv"
        buildConfigField("String", "ANIME_CALENDAR_TV_API_BASE_URL", "\"https://calendar-api.peden88.stream\"")
    }

    signingConfigs {
        create("release") {
            val path = System.getenv("ANIME_TV_KEYSTORE_FILE")
            if (!path.isNullOrBlank()) {
                storeFile = rootProject.file(path)
                storePassword = System.getenv("ANIME_TV_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANIME_TV_KEY_ALIAS")
                keyPassword = System.getenv("ANIME_TV_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.foundation:foundation-layout")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
}
