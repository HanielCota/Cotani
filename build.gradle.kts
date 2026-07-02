import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

plugins {
    `java-library`
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
}

abstract class ValidateModuleArchitecture : DefaultTask() {

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @get:Input
    abstract val modules: ListProperty<String>

    @TaskAction
    fun validate() {
        val root = rootDirectory.asFile.get()
        val moduleNames = modules.get()
        val moduleSet = moduleNames.toSet()
        val importPattern =
            Regex("""^import\s+com\.cotani\.(${moduleNames.joinToString("|")})\.(impl|internal)\.""")
        val apiImportPattern = Regex("""^import\s+com\.cotani\..*\.(impl|internal)\.""")
        val violations = mutableListOf<String>()

        moduleNames.forEach { module ->
            val sourceRoot = root.resolve("cotani-$module/src")
            if (!sourceRoot.exists()) {
                return@forEach
            }

            sourceRoot
                .walkTopDown()
                .filter { file -> file.isFile && file.extension == "java" }
                .forEach { file ->
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            val match = importPattern.find(line)
                            val targetModule = match?.groupValues?.get(1)
                            if (targetModule != null && targetModule != module) {
                                violations +=
                                    "${file.relativeTo(root)}:${index + 1} imports another module implementation: ${line.trim()}"
                            }
                        }
                    }
                }
        }

        root.walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.extension == "java" &&
                    file.invariantSeparatorsPath.contains("/src/main/java/") &&
                    file.invariantSeparatorsPath.contains("/api/")
            }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        if (apiImportPattern.containsMatchIn(line)) {
                            violations += "${file.relativeTo(root)}:${index + 1} API imports implementation: ${line.trim()}"
                        }
                    }
                }
            }

        val dependencies = moduleNames.associateWith { module ->
            val buildFile = root.resolve("cotani-$module/build.gradle.kts")
            if (!buildFile.exists()) {
                emptySet()
            } else {
                Regex("""project\(":([^"]+)"\)""")
                    .findAll(buildFile.readText())
                    .map { match -> match.groupValues[1] }
                    .filter { dependency -> dependency in moduleSet }
                    .toSet()
            }
        }

        val cycles = mutableSetOf<String>()

        fun visit(start: String, current: String, path: List<String>) {
            dependencies[current].orEmpty().forEach { next ->
                when {
                    next == start -> cycles += (path + next).joinToString(" -> ")
                    next !in path -> visit(start, next, path + next)
                }
            }
        }

        moduleNames.forEach { module -> visit(module, module, listOf(module)) }
        cycles.forEach { cycle -> violations += "Gradle module dependency cycle: $cycle" }

        if (violations.isNotEmpty()) {
            throw GradleException("Module architecture validation failed:\n" + violations.joinToString("\n"))
        }
    }
}

group = "com.cotani"
description = "Cotani — modular Paper library"

allprojects {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

subprojects {
    pluginManager.withPlugin("java-library") {
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
            }
            withSourcesJar()
            withJavadocJar()
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.compilerArgs.addAll(listOf("-Werror", "-Xlint:all"))
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            jvmArgs("-Dnet.bytebuddy.experimental=true")
        }

        tasks.withType<Javadoc>().configureEach {
            val docletOptions = options as StandardJavadocDocletOptions
            docletOptions.addStringOption("Xdoclint:-missing", "-quiet")

            val sourceFiles = source.filter { file ->
                file.name != "package-info.java" && file.name.endsWith(".java")
            }
            if (sourceFiles.isEmpty) {
                enabled = false
            }
        }
    }

    pluginManager.withPlugin("net.ltgt.errorprone") {
        tasks.withType<JavaCompile>().configureEach {
            options.errorprone {
                disableWarningsInGeneratedCode.set(true)
                disable("StringConcatToTextBlock")
                disable("NotJavadoc")
                error("NullAway")
                option("NullAway:AnnotatedPackages", "com.cotani")
                option("NullAway:AcknowledgeRestrictiveAnnotations", "true")
            }
        }
    }

    pluginManager.withPlugin("com.diffplug.spotless") {
        configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            java {
                palantirJavaFormat("2.94.0")
            }
        }
    }
}

val validateModuleArchitecture = tasks.register<ValidateModuleArchitecture>("validateModuleArchitecture") {
    group = "verification"
    description = "Validates Cotani module boundaries and Gradle dependency cycles."
    rootDirectory.set(layout.projectDirectory)
    modules.set(listOf("core", "task", "text", "item", "config", "storage", "cache", "teleport", "user", "economy"))
}

tasks.named("check") {
    dependsOn(validateModuleArchitecture)
}
