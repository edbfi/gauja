// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("gauja.android.library")
    id("gauja.android.hilt")
    alias(libs.plugins.kotlin.serialization)
    id("gauja.android.compose")
}

dependencies {
    testImplementation(libs.junit)
    implementation(libs.serialization.core)
    implementation(libs.navigation.runtime)
}
