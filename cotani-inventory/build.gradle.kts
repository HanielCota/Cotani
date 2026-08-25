description = "Cotani — loss-less inventory synchronization, snapshots, and cross-server transfer locks"

dependencies {
    compileOnlyApi(libs.paper.api)
    api(libs.jspecify)
    api(project(":core"))
    api(project(":task"))
    api(project(":storage"))

    implementation(project(":text"))

    testImplementation(libs.paper.api)
}
