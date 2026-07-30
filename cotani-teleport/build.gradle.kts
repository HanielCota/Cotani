import org.gradle.jvm.tasks.Jar

description = "Cotani — modern teleport library for Paper"

dependencies {
    api(project(":core"))
    api(project(":task"))
    api(project(":cooldown"))
    api(project(":text"))
    implementation(project(":config"))
    compileOnlyApi(libs.paper.api)
    api(libs.jspecify)

    testImplementation(libs.paper.api)
}

val verifyLibraryJar = tasks.register("verifyLibraryJar") {
    group = "verification"
    description = "Ensures the teleport library cannot be mistaken for a standalone Paper plugin."
    val libraryJar = tasks.named<Jar>("jar")
    dependsOn(libraryJar)
    doLast {
        val pluginDescriptors = zipTree(libraryJar.get().archiveFile).matching {
            include("plugin.yml", "paper-plugin.yml")
        }.files
        check(pluginDescriptors.isEmpty()) {
            "Library jar must not contain a Paper plugin descriptor: $pluginDescriptors"
        }
    }
}

tasks.named("check") {
    dependsOn(verifyLibraryJar)
}
