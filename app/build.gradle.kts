plugins {
    id("com.android.application")
}

android {
    namespace = "com.winterfarmer.mysofalizer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.winterfarmer.mysofalizer"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.1 WinterFarmer"

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}
