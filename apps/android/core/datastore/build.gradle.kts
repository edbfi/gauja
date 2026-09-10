// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("gauja.android.library")
    id("gauja.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.datastore.core)
    implementation(libs.datastore.preferences)
    implementation(libs.serialization.protobuf)
    implementation(libs.coroutines)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
}

// Kotlin 2.x includes the common standard library. DataStore 1.1's legacy
// coordinates otherwise produce different lock graphs in AGP's compiler/ASM views.
configurations.configureEach {
    if (name.startsWith("debug") || name.startsWith("release")) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-common")
    }
}
