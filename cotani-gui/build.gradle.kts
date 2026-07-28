description = "Cotani — reactive GUI module for Paper and Folia"

dependencies {
    api(project(":text"))
    api(project(":item"))

    compileOnlyApi(libs.paper.api)
    api(libs.jspecify)

    testImplementation(libs.paper.api)
}
