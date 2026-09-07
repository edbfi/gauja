// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("gauja.android.library")
    // Compose compiler also processes the repository-to-render acceptance test.
    id("gauja.android.compose")
    alias(libs.plugins.kotlin.serialization)
    id("gauja.android.hilt")
}

dependencies {
    compileOnly(libs.compose.runtime)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.serialization)
    implementation(libs.coroutines)
    implementation(libs.coil.core)
    implementation(libs.immutable)
    implementation(project(":core:api"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:compat"))
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.test)
    testImplementation(libs.compose.foundation)
    testImplementation(libs.sqlite)
    testImplementation(libs.room.runtime)
    testImplementation(libs.turbine)
    testImplementation(libs.junit)
    debugRuntimeOnly(libs.compose.test.manifest)
    testImplementation(project(":core:testing"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
