description = "Cotani — modern Display Entity engine for Paper and Folia"

dependencies {
    api(project(":core"))
    api(project(":task"))
    api(project(":text"))
    api(project(":item"))

    compileOnlyApi(libs.paper.api)
    api(libs.jspecify)

    testImplementation(libs.paper.api)
}
