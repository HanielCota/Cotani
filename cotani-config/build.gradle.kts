plugins {
    `java-library`
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
}

description = "Cotani — configuration framework"

dependencies {
    api(project(":core"))
    api(project(":task"))
    api(project(":text"))

    compileOnlyApi(libs.paper.api)
    api(libs.jspecify)

    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.text.minimessage)

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
