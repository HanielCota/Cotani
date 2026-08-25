description = "Cotani — locale preferences, message catalogs, fallback and MiniMessage rendering"

dependencies {
    api(project(":core"))
    api(project(":text"))
    api(libs.adventure.api)
    api(libs.adventure.text.minimessage)
    api(libs.jspecify)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.paper.api)
}
