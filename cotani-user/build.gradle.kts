plugins {
    `java-library`
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
}

description = "Cotani — user lifecycle and online cache"

dependencies {
    compileOnlyApi(libs.paper.api)
    api(libs.jspecify)
    api(project(":storage"))
    api(project(":task"))

    implementation(project(":core"))
    implementation(project(":text"))

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
