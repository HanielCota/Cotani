description = "Cotani — modern teleport module for Paper"

dependencies {
    api(project(":core"))
    api(project(":task"))
    api(project(":cooldown"))
    api(project(":text"))
    implementation(project(":config"))
    compileOnlyApi(libs.paper.api)
    api(libs.jspecify)

    testImplementation(libs.paper.api)
}

tasks.processResources {
    val projectVersion = project.version.toString()
    filesMatching("plugin.yml") {
        expand("version" to projectVersion)
    }
}
