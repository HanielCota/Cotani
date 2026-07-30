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
                val source = file.readText()
                if (Regex("""(?m)^public\s+(?:final\s+|abstract\s+)?(?:class|record|interface|enum|sealed\s+interface)\s+""")
                        .containsMatchIn(source) &&
                    !source.contains("@com.cotani.api.InternalApi")) {
                    violations +=
                        "${file.relativeTo(root)} exposes an unmarked implementation; add @InternalApi or move the contract out of impl/internal"
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
