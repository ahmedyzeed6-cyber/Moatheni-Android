plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.moatheni.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.moatheni.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        create("release") {
            storeFile = file("moatheni-release.jks")
            storePassword = System.getenv("MOATHENI_STORE_PASSWORD")
            keyAlias = System.getenv("MOATHENI_KEY_ALIAS")
            keyPassword = System.getenv("MOATHENI_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
}
