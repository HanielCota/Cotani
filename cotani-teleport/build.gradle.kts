import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.gradle.jvm.tasks.Jar

description = "Cotani — modern teleport library for Paper"

@DisableCachingByDefault(because = "Performs a lightweight archive validation")
abstract class VerifyLibraryJar : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val archiveFile: RegularFileProperty

    @TaskAction
    fun verifyArchive() {
        val descriptors = ZipFile(archiveFile.get().asFile).use { archive ->
            listOf("plugin.yml", "paper-plugin.yml").filter { archive.getEntry(it) != null }
        }
        check(descriptors.isEmpty()) {
            "Library jar must not contain a Paper plugin descriptor: $descriptors"
        }
    }
}

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

val libraryJar = tasks.named<Jar>("jar")
val verifyLibraryJar = tasks.register<VerifyLibraryJar>("verifyLibraryJar") {
    group = "verification"
    description = "Ensures the teleport library cannot be mistaken for a standalone Paper plugin."
    dependsOn(libraryJar)
    archiveFile.set(libraryJar.flatMap { it.archiveFile })
}

tasks.named("check") {
    dependsOn(verifyLibraryJar)
}
