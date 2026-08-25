description = "Cotani — high-performance placeholder engine and expansion bridge for Paper and Folia"

dependencies {
    api(project(":core"))
    api(project(":task"))
    api(project(":text"))

    compileOnlyApi(libs.paper.api)
    compileOnly(libs.placeholderapi)
    api(libs.jspecify)

    testImplementation(libs.paper.api)
}
