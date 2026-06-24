plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.aetherscreen.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aetherscreen"
        minSdk = 30 // Wear OS 3.0+
        targetSdk = 35
        versionCode = 104
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("/Users/user/AndroidStudioProjects/Games/Snake/dpad_arcade_release.jks")
            storePassword = "dpadhero123"
            keyAlias = "dpad_hero_alias"
            keyPassword = "dpadhero123"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.5"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlin.stdlib)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Compose Core
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Wear OS Compose
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)

    // Play Services Wearable
    implementation(libs.play.services.wearable)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.compose.ui.tooling)
}
