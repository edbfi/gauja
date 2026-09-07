// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Gauja"

include(
    ":app",
    ":core:api",
    ":core:model",
    ":core:common",
    ":core:compat",
    ":core:network",
    ":core:data",
    ":core:testing",
    ":core:ui",
    ":core:database",
    ":core:datastore",
    ":core:designsystem",
    ":core:navigation",
    ":feature:servers",
)
