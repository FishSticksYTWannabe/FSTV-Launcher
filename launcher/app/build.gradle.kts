plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fstv.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fstv.launcher"
        minSdk = 25
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // A release build runs noticeably faster than a debuggable one on this TV.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    lint {
        // The target SDK is deliberately old to match Fire OS 6.
        checkReleaseBuilds = false
        abortOnError = false
    }
}
