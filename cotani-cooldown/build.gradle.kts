import net.ltgt.gradle.errorprone.errorprone

description = "Cotani - basic cooldown module"

dependencies {
    api(project(":core"))
    api(project(":task"))
    api(project(":cache"))
    api(project(":storage"))
    api(project(":config"))

    compileOnlyApi(libs.paper.api)
    api(libs.jspecify)

    testImplementation(libs.paper.api)
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        excludedPaths.set(".*/CacheCooldownStore\\.java")
    }
}
