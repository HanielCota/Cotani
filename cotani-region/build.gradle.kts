description = "Cotani — high-performance 3D spatial region and protection module for Paper and Folia"

dependencies {
    api(project(":core"))
    api(project(":task"))
    api(project(":text"))

    compileOnlyApi(libs.paper.api)
    api(libs.jspecify)

    testImplementation(libs.paper.api)
}
