// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    alias(libs.plugins.android.application)
    id("gauja.android.compose")
    alias(libs.plugins.kotlin.serialization)
    id("gauja.android.hilt")
}

android {
    namespace = "app.gauja"
    compileSdk = 37
    defaultConfig {
        applicationId = "app.gauja"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "app.gauja.HiltTestRunner"
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { lintConfig = rootProject.file("config/lint.xml") }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    // Compose's older transitive Espresso uses InputManager APIs removed in API 37.
    constraints { androidTestImplementation(libs.espresso) }
    implementation(project(":feature:servers"))
    implementation(project(":core:data"))
    implementation(project(":core:datastore"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(libs.compose.foundation)
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(libs.activity.compose)
    implementation(libs.navigation.ui)
    implementation(libs.navigation.runtime)
    implementation(libs.lifecycle.navigation)
    androidTestImplementation(project(":core:data"))
    androidTestImplementation(project(":core:datastore"))
    androidTestImplementation(project(":core:common"))
    androidTestImplementation(project(":core:model"))
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.androidx.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.hilt.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugRuntimeOnly(libs.compose.test.manifest)
}
