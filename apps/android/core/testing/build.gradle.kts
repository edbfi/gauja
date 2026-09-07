// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("gauja.android.library")
    id("gauja.android.compose")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(project(":core:model"))
    implementation(project(":core:datastore"))
    implementation(libs.coroutines.test)
    implementation(libs.junit)
}
