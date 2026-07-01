plugins {
    `java-library`
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
}

description = "Cotani — modern teleport module for Paper"

dependencies {
    api(project(":core"))
    api(project(":task"))
    compileOnlyApi(libs.paper.api)
    api(libs.jspecify)

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.processResources {
    val projectVersion = project.version.toString()
    filesMatching("plugin.yml") {
        expand("version" to projectVersion)
    }
}
