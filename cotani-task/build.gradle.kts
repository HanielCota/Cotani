plugins {
    `java-library`
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
}

description = "Cotani — async task chaining and executors"

dependencies {
    api(project(":core"))
    api(libs.jspecify)

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.paper.api)
    testRuntimeOnly(libs.junit.platform.launcher)
}

