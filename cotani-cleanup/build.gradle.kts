description = "Cotani - safe asynchronous world entity cleanup"

dependencies {
    api(project(":core"))
    api(project(":event"))
    api(project(":task"))
    api(libs.jspecify)

    compileOnlyApi(libs.paper.api)

    testImplementation(libs.paper.api)
}
