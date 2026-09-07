// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("gauja.android.library")
    alias(libs.plugins.kotlin.serialization)
    id("gauja.android.hilt")
}

dependencies {
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.serialization)
    implementation(libs.coroutines)
    implementation(libs.immutable)
    implementation(project(":core:api"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:compat"))
    testImplementation(libs.junit)
    testImplementation(project(":core:testing"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
