description = "Cotani — reactive zero-flicker HUD module for Paper and Folia"

dependencies {
    api(project(":core"))
    api(project(":task"))
    api(project(":text"))
    api(project(":gui"))

    compileOnlyApi(libs.paper.api)
    api(libs.jspecify)

    testImplementation(libs.paper.api)
}
