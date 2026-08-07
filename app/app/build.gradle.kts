plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mschiller890.paddington"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mschiller890.paddington"
        minSdk = 28
        targetSdk = 36
        versionCode = (project.findProperty("versionCode") as String? ?: "1").toInt()
        versionName = project.findProperty("versionName") as String? ?: "1.0"
    }

    signingConfigs {
        create("paddington") {
            storeFile = rootProject.file("../paddington.keystore")
            storePassword = "paddington"
            keyAlias = "paddington"
            keyPassword = "paddington"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("paddington")
        }
        debug {
            signingConfig = signingConfigs.getByName("paddington")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    compileOnly(files("libs/api-82.jar"))
    debugImplementation(libs.androidx.compose.ui.tooling)
}