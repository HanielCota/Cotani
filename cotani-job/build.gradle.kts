description = "Cotani — persistent asynchronous jobs with retries and recovery"

dependencies {
    api(project(":task"))
    api(libs.jspecify)

    compileOnlyApi(libs.paper.api)

    testImplementation(libs.paper.api)
}
