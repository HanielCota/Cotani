description = "Cotani — user lifecycle and online cache"

dependencies {
    compileOnlyApi(libs.paper.api)
    api(libs.jspecify)
    api(project(":storage"))
    api(project(":task"))

    implementation(project(":core"))
    implementation(project(":text"))

    testImplementation(libs.paper.api)
}
