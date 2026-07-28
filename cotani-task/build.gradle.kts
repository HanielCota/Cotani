description = "Cotani — async task chaining and executors"

dependencies {
    api(project(":core"))
    api(libs.jspecify)

    compileOnlyApi(libs.paper.api)

    testImplementation(libs.paper.api)
}
