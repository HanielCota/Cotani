description = "Cotani - type-safe record configuration module"

dependencies {
    api(project(":core"))
    api(project(":task"))
    api(project(":text"))

    compileOnlyApi(libs.paper.api)
    api(libs.jspecify)

    testImplementation(libs.jimfs)
    testImplementation(libs.paper.api)
}
