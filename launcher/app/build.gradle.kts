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
        // Encodes the semver: MAJOR * 10000 + MINOR * 100 + PATCH.
        // Room for 99 patches per minor and 99 minors per major, so 0.2.1 -> 201, 0.2.47 -> 247,
        // 0.3.0 -> 300, 1.0.0 -> 10000. Android only cares that this grows across releases.
        versionCode = 200
        versionName = "0.2.0"
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
