plugins {
    `java-library`
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
}

description = "Cotani — fluent ItemStack builders"

dependencies {
    api(project(":core"))
    api(project(":text"))
    implementation(libs.caffeine)

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.paper.api)
    testRuntimeOnly(libs.junit.platform.launcher)
}

