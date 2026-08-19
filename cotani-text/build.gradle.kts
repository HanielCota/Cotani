description = "Cotani — MiniMessage, placeholders and Adventure audience utilities"

dependencies {
    api(project(":core"))
    api(libs.caffeine)

    compileOnlyApi(libs.paper.api)

    testImplementation(libs.paper.api)
}
