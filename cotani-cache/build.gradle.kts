description = "Cotani - caffeine and player cache module"

dependencies {
    api(project(":core"))
    api(project(":task"))
    api(project(":storage"))
    api(project(":config"))

    compileOnlyApi(libs.paper.api)
    api(libs.jspecify)
    api(libs.caffeine)

    testImplementation(libs.paper.api)
}
