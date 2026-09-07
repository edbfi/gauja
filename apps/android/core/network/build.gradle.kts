// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("gauja.android.library")
    id("gauja.android.hilt")
}

dependencies {
    implementation(libs.coroutines)
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))
    implementation(libs.okhttp)
    implementation(project(":core:model"))
    testImplementation(libs.junit)
    testImplementation(project(":core:testing"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
