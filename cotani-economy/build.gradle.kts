plugins {
    `java-library`
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
}

description = "Cotani — economy module"

dependencies {
    api(project(":core"))
    api(project(":task"))
    api(project(":storage"))
    api(project(":config"))
    api(libs.jspecify)
    implementation(libs.caffeine)

    compileOnlyApi(libs.paper.api)

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
