description = "Cotani - economy module"

dependencies {
    api(project(":core"))
    api(project(":task"))
    api(project(":storage"))
    api(project(":config"))
    api(project(":text"))
    api(libs.jspecify)
    implementation(libs.caffeine)

    compileOnlyApi(libs.paper.api)

    testImplementation(libs.paper.api)
}
