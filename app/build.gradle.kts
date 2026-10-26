plugins {
    id("com.android.application")
}

android {
    namespace = "com.rusty.lookout"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rusty.lookout"
        // Family Room TV is a Sony BRAVIA on Android TV 12 (API 31).
        minSdk = 31
        targetSdk = 34
        versionCode = 11
        versionName = "2.9"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sideloaded over adb only, so the debug key is fine.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
