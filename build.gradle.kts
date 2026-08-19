import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer

plugins {
    base
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.spotless) apply false
}

// The type-safe `libs` accessor is not resolvable inside the `subprojects` block,
// so capture the catalog entries used by shared configuration here.
val javaToolchainVersion = libs.versions.java.get()
val errorproneCore = libs.errorprone.core
val nullawayProcessor = libs.nullaway
val junitJupiter = libs.junit.jupiter
val mockitoCore = libs.mockito.core
val junitPlatformLauncher = libs.junit.platform.launcher

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
            Regex("""^import\s+(?:com|net)\.cotani\.(${moduleNames.joinToString("|")})\.(impl|internal)\.""")
        val apiImportPattern = Regex("""^import\s+(?:com|net)\.cotani\..*\.(impl|internal)\.""")
        val violations = mutableListOf<String>()

        root.walkTopDown()
            .filter { file ->
                file.isFile &&
                        file.extension == "java" &&
                        file.invariantSeparatorsPath.contains("/src/main/java/") &&
                        (file.invariantSeparatorsPath.contains("/internal/") ||
                                file.invariantSeparatorsPath.contains("/impl/"))
            }
            .forEach { file ->
                runCatching {
                    val source = file.readText()
                    if (Regex("""(?m)^public\s+(?:final\s+|abstract\s+)?(?:class|record|interface|enum|sealed\s+interface)\s+""")
                            .containsMatchIn(source) &&
                        !source.contains("@InternalApi") &&
                        !source.contains("@com.cotani.api.InternalApi")) {
                        violations +=
                            "${file.relativeTo(root)} exposes an unmarked implementation; add @InternalApi or move the contract out of impl/internal"
                    }
                }
            }

        moduleNames.forEach { module ->
            val sourceRoot = root.resolve("cotani-$module/src")
            if (!sourceRoot.exists()) {
                return@forEach
            }

            sourceRoot
                .walkTopDown()
                .filter { file -> file.isFile && file.extension == "java" }
                .forEach { file ->
                    runCatching {
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
        }

        root.walkTopDown()
            .filter { file ->
                file.isFile &&
                        file.extension == "java" &&
                        file.invariantSeparatorsPath.contains("/src/main/java/") &&
                        file.invariantSeparatorsPath.contains("/api/")
            }
            .forEach { file ->
                runCatching {
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            if (apiImportPattern.containsMatchIn(line)) {
                                violations += "${file.relativeTo(root)}:${index + 1} API imports implementation: ${line.trim()}"
                            }
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

        val bomFile = root.resolve("cotani-bom/build.gradle.kts")
        if (bomFile.exists()) {
            val bomContent = bomFile.readText()
            val bomConstraints = Regex("""api\(project\(":([^"]+)"\)\)""")
                .findAll(bomContent)
                .map { it.groupValues[1] }
                .toSet()

            val missingFromBom = moduleSet - bomConstraints
            if (missingFromBom.isNotEmpty()) {
                violations += "cotani-bom is missing constraints for published modules: ${missingFromBom.sorted().joinToString(", ")}"
            }

            val unexpectedInBom = bomConstraints - moduleSet
            if (unexpectedInBom.isNotEmpty()) {
                violations += "cotani-bom contains constraints for non-published or unknown modules: ${unexpectedInBom.sorted().joinToString(", ")}"
            }
        } else {
            violations += "cotani-bom/build.gradle.kts does not exist"
        }

        if (violations.isNotEmpty()) {
            throw GradleException("Module architecture validation failed:\n" + violations.joinToString("\n"))
        }
    }
}

abstract class ValidateDocumentation : DefaultTask() {

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @TaskAction
    fun validate() {
        val root = rootDirectory.asFile.get()
        val markdownFiles = root.walkTopDown()
            .onEnter { directory -> directory.name !in setOf(".git", ".gradle", "build") }
            .filter { file -> file.isFile && file.extension == "md" }
            .toList()
        val linkPattern = Regex("""\[[^]]+]\(([^)]+)\)""")
        val staleSymbols = mapOf(
            "com.cotani.core.Cotani" to "use com.cotani.Cotani",
            "DefaultEventBus.createDefault(" to "use DefaultEventBus.create(...) with an explicit executor",
            "DefaultCooldownService.inMemory(" to "use the public CotaniCooldowns factory",
            ".thenRun(" to "TaskChain does not expose thenRun; use an explicit target transition",
            "file:///D:/Cotani" to "repository documentation must use relative links",
        )
        val violations = mutableListOf<String>()

        markdownFiles.forEach { file ->
            val content = file.readText()
            staleSymbols.forEach { (symbol, guidance) ->
                if (content.contains(symbol)) {
                    violations += "${file.relativeTo(root)} contains '$symbol'; $guidance"
                }
            }

            linkPattern.findAll(content).forEach { match ->
                val rawTarget = match.groupValues[1].substringBefore('#').substringBefore('?')
                if (rawTarget.isBlank() ||
                    rawTarget.startsWith("#") ||
                    rawTarget.contains("://") ||
                    rawTarget.startsWith("mailto:")) {
                    return@forEach
                }
                val resolved = file.parentFile.toPath().resolve(rawTarget).normalize().toFile()
                if (!resolved.exists()) {
                    violations += "${file.relativeTo(root)} links to missing path '$rawTarget'"
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException("Documentation validation failed:\n" + violations.joinToString("\n"))
        }
    }
}

group = "com.cotani"
description = "Cotani — modular Paper library"

subprojects {
    group = "com.cotani"
    val isPlatform = name == "bom"
    val isExample = name == "examples"

    apply(plugin = if (isPlatform) "java-platform" else "java-library")
    if (!isExample) {
        apply(plugin = "maven-publish")
    }
    if (!isPlatform) {
        apply(plugin = "net.ltgt.errorprone")
        apply(plugin = "com.diffplug.spotless")
    }

    if (!isPlatform) {
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(javaToolchainVersion))
            }
            withSourcesJar()
            withJavadocJar()
        }
    }

    if (!isExample) {
        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components[if (isPlatform) "javaPlatform" else "java"])
                    artifactId = "cotani-${project.name}"
                    pom {
                        name.set("cotani-${project.name}")
                        description.set(project.description ?: "Cotani — ${project.name} module")
                    }
                }
            }
        }
    }

    if (!isPlatform) {
        dependencies {
            "errorprone"(errorproneCore)
            "errorprone"(nullawayProcessor)

            "testImplementation"(junitJupiter)
            "testImplementation"(mockitoCore)
            "testRuntimeOnly"(junitPlatformLauncher)
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.compilerArgs.addAll(listOf("-Werror", "-Xlint:all"))
            options.errorprone {
                disableWarningsInGeneratedCode.set(true)
                disable("StringConcatToTextBlock")
                disable("NotJavadoc")
                error("NullAway")
                option("NullAway:AnnotatedPackages", "com.cotani,net.cotani")
                option("NullAway:AcknowledgeRestrictiveAnnotations", "true")
            }
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            jvmArgs("-Dnet.bytebuddy.experimental=true")
        }

        tasks.named<Test>("test") {
            exclude("**/*IntegrationTest.class")
        }

        val testSourceSet = extensions.getByType<SourceSetContainer>()["test"]
        tasks.register<Test>("integrationTest") {
            group = "verification"
            description = "Runs Docker-backed integration tests separately from unit tests."
            testClassesDirs = testSourceSet.output.classesDirs
            classpath = testSourceSet.runtimeClasspath
            include("**/*IntegrationTest.class")
            shouldRunAfter(tasks.named("test"))
        }

        tasks.withType<Javadoc>().configureEach {
            val docletOptions = options as StandardJavadocDocletOptions
            docletOptions.addStringOption("Xdoclint:-missing", "-quiet")

            val sourceFiles = source.filter { file ->
                val path = file.invariantSeparatorsPath
                file.name != "package-info.java" &&
                        file.name.endsWith(".java") &&
                        !path.contains("/impl/") &&
                        !path.contains("/internal/")
            }
            setSource(sourceFiles)
            if (sourceFiles.isEmpty) {
                enabled = false
            }
        }

        configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            java {
                palantirJavaFormat("2.96.0")
            }
        }
    }
}

val validateModuleArchitecture = tasks.register<ValidateModuleArchitecture>("validateModuleArchitecture") {
    group = "verification"
    description = "Validates Cotani module boundaries and Gradle dependency cycles."
    rootDirectory.set(layout.projectDirectory)
    modules.set(listOf("core", "task", "text", "item", "config", "storage", "cache", "teleport", "user", "economy", "cooldown", "event", "metrics", "gui"))
}

val validateDocumentation = tasks.register<ValidateDocumentation>("validateDocumentation") {
    group = "verification"
    description = "Validates documentation links and rejects known stale API examples."
    rootDirectory.set(layout.projectDirectory)
}

tasks.named("check") {
    dependsOn(validateModuleArchitecture)
    dependsOn(validateDocumentation)
}

val aggregateJavadoc = tasks.register<Sync>("aggregateJavadoc") {
    group = "documentation"
    description = "Aggregates generated Javadocs from all modules for GitHub Pages."
    val publicProjects = subprojects.filter { it.name !in setOf("bom", "examples") }
    dependsOn(publicProjects.map { it.tasks.named("javadoc") })

    into(layout.buildDirectory.dir("docs/javadoc"))
    publicProjects.forEach { proj ->
        from(proj.tasks.named<Javadoc>("javadoc").map { it.destinationDir }) {
            into(proj.name)
        }
    }
}

abstract class GenerateJavadocIndex : DefaultTask() {
    @get:Input
    abstract val moduleDescriptions: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val target = outputDirectory.get().asFile
        if (!target.exists()) {
            target.mkdirs()
        }
        val modulesList = moduleDescriptions.get().entries
            .sortedBy { it.key }
            .joinToString("\n") { (name, desc) ->
                """<li><a href="$name/index.html"><strong>cotani-$name</strong></a> - $desc</li>"""
            }
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Cotani API Documentation</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 800px; margin: 40px auto; padding: 0 20px; line-height: 1.6; color: #24292e; }
                    h1 { border-bottom: 1px solid #eaecef; padding-bottom: 0.3em; color: #0969da; }
                    ul { list-style-type: none; padding-left: 0; }
                    li { margin: 12px 0; padding: 12px; border: 1px solid #d0d7de; border-radius: 6px; background-color: #f6f8fa; }
                    a { color: #0969da; text-decoration: none; font-size: 1.1em; }
                    a:hover { text-decoration: underline; }
                </style>
            </head>
            <body>
                <h1>Cotani API Documentation</h1>
                <p>Welcome to the official API documentation for Cotani modular components.</p>
                <ul>
                    $modulesList
                </ul>
            </body>
            </html>
        """.trimIndent()
        target.resolve("index.html").writeText(html)
    }
}

val generateJavadocIndex = tasks.register<GenerateJavadocIndex>("generateJavadocIndex") {
    group = "documentation"
    description = "Generates an index.html landing page for aggregated Javadocs."
    outputDirectory.set(layout.buildDirectory.dir("docs/javadoc"))
    val publicProjects = subprojects.filter { it.name !in setOf("bom", "examples") }
    moduleDescriptions.set(publicProjects.associate { it.name to (it.description ?: "") })
}

aggregateJavadoc.configure {
    finalizedBy(generateJavadocIndex)
}
