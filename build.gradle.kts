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

        val metadata = mapOf(
            "core" to Pair("🧱", "Foundation"),
            "task" to Pair("⚡", "Foundation"),
            "text" to Pair("💬", "Foundation"),
            "item" to Pair("⚔️", "Foundation"),
            "config" to Pair("⚙️", "Infrastructure"),
            "storage" to Pair("💾", "Infrastructure"),
            "cache" to Pair("🧠", "Infrastructure"),
            "user" to Pair("👤", "Gameplay"),
            "economy" to Pair("💰", "Gameplay"),
            "cooldown" to Pair("⏱️", "Gameplay"),
            "teleport" to Pair("🌀", "Gameplay"),
            "event" to Pair("📢", "Gameplay"),
            "gui" to Pair("📦", "Gameplay"),
            "metrics" to Pair("📊", "Operations")
        )

        val defaultDescriptions = mapOf(
            "core" to "Centralized plugin lifecycle ownership and reverse-order resource disposal.",
            "task" to "Async, global, region, and entity scheduling with fluent TaskChain thread transitions.",
            "text" to "MiniMessage text formatting, audience messaging, and placeholder resolution.",
            "item" to "Fluent Paper 1.21+ data-component item, armor, and player skull builders.",
            "config" to "YAML binding to immutable records with constraint validation and async reloads.",
            "storage" to "SQLite, MySQL, and MariaDB queries, schema migrations, and transaction management.",
            "cache" to "Caffeine-backed caches with automatic dirty tracking, bulk flushing, and persistence.",
            "user" to "Async user profile resolution, online caching, and session lifecycle management.",
            "economy" to "Exact BigDecimal economy with atomic transactions and idempotency guarantees.",
            "cooldown" to "Local and distributed SQL-backed cooldown limiters with automatic expiration pruning.",
            "teleport" to "Policy-driven teleport pipelines with hazard checks, combat tags, and countdown warps.",
            "event" to "Reflection-free, high-performance event bus with execution order prioritizations.",
            "gui" to "Declarative reactive inventory interfaces, pagination, and anti-exploit click debounce.",
            "metrics" to "Micrometer metrics instrumentation with optional Prometheus HTTP exporter endpoint."
        )

        val cards = moduleDescriptions.get().keys.sorted().joinToString("\n") { name ->
            val meta = metadata[name] ?: Pair("📦", "Module")
            val icon = meta.first
            val category = meta.second
            val desc = defaultDescriptions[name] ?: moduleDescriptions.get()[name] ?: "Cotani module"
            """
            <a href="$name/index.html" class="card" data-category="$category" data-name="cotani-$name $desc">
                <div class="card-header">
                    <div class="card-icon">$icon</div>
                    <span class="badge badge-$category">$category</span>
                </div>
                <h3 class="card-title">cotani-$name</h3>
                <p class="card-desc">$desc</p>
                <div class="card-footer">
                    <span>Explore Javadocs</span>
                    <svg class="arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
                </div>
            </a>
            """.trimIndent()
        }

        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Cotani — API Documentation</title>
                <link rel="preconnect" href="https://fonts.googleapis.com">
                <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;600&display=swap" rel="stylesheet">
                <style>
                    :root {
                        --bg: #090d16;
                        --bg-card: #111827;
                        --bg-card-hover: #172033;
                        --border: #1f293d;
                        --border-hover: #3b82f6;
                        --text: #f3f4f6;
                        --text-muted: #9ca3af;
                        --accent-blue: #3b82f6;
                        --accent-cyan: #06b6d4;
                        --accent-indigo: #6366f1;
                        --accent-emerald: #10b981;
                        --accent-amber: #f59e0b;
                        --radius: 14px;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background: var(--bg);
                        color: var(--text);
                        min-height: 100vh;
                        display: flex;
                        flex-direction: column;
                        line-height: 1.6;
                        background-image: 
                            radial-gradient(circle at 15% 10%, rgba(59, 130, 246, 0.12) 0%, transparent 40%),
                            radial-gradient(circle at 85% 20%, rgba(99, 102, 241, 0.12) 0%, transparent 40%);
                    }
                    .container {
                        max-width: 1200px;
                        margin: 0 auto;
                        padding: 48px 24px;
                        width: 100%;
                    }
                    header {
                        text-align: center;
                        margin-bottom: 48px;
                    }
                    .hero-tag {
                        display: inline-flex;
                        align-items: center;
                        gap: 8px;
                        padding: 6px 16px;
                        background: rgba(59, 130, 246, 0.1);
                        border: 1px solid rgba(59, 130, 246, 0.25);
                        border-radius: 9999px;
                        font-size: 0.85rem;
                        font-weight: 600;
                        color: #60a5fa;
                        margin-bottom: 20px;
                    }
                    h1 {
                        font-size: 3rem;
                        font-weight: 800;
                        letter-spacing: -0.03em;
                        background: linear-gradient(135deg, #ffffff 0%, #cbd5e1 50%, #60a5fa 100%);
                        -webkit-background-clip: text;
                        -webkit-text-fill-color: transparent;
                        margin-bottom: 16px;
                    }
                    .subtitle {
                        font-size: 1.15rem;
                        color: var(--text-muted);
                        max-width: 680px;
                        margin: 0 auto 32px;
                    }
                    .nav-links {
                        display: flex;
                        justify-content: center;
                        gap: 12px;
                        flex-wrap: wrap;
                    }
                    .nav-btn {
                        display: inline-flex;
                        align-items: center;
                        gap: 8px;
                        padding: 10px 20px;
                        border-radius: 10px;
                        font-size: 0.92rem;
                        font-weight: 600;
                        text-decoration: none;
                        transition: all 0.2s ease;
                    }
                    .nav-btn-primary {
                        background: linear-gradient(135deg, #2563eb, #3b82f6);
                        color: #ffffff;
                        box-shadow: 0 4px 14px rgba(37, 99, 235, 0.35);
                    }
                    .nav-btn-primary:hover {
                        transform: translateY(-1px);
                        box-shadow: 0 6px 20px rgba(37, 99, 235, 0.45);
                    }
                    .nav-btn-secondary {
                        background: var(--bg-card);
                        color: var(--text);
                        border: 1px solid var(--border);
                    }
                    .nav-btn-secondary:hover {
                        background: var(--bg-card-hover);
                        border-color: #4b5563;
                        transform: translateY(-1px);
                    }
                    .controls {
                        display: flex;
                        flex-direction: column;
                        gap: 16px;
                        margin-bottom: 36px;
                    }
                    @media (min-width: 768px) {
                        .controls {
                            flex-direction: row;
                            justify-content: space-between;
                            align-items: center;
                        }
                    }
                    .search-bar {
                        position: relative;
                        flex: 1;
                        max-width: 420px;
                    }
                    .search-bar input {
                        width: 100%;
                        padding: 12px 16px 12px 42px;
                        background: var(--bg-card);
                        border: 1px solid var(--border);
                        border-radius: 10px;
                        color: var(--text);
                        font-family: inherit;
                        font-size: 0.95rem;
                        transition: all 0.2s ease;
                    }
                    .search-bar input:focus {
                        outline: none;
                        border-color: var(--accent-blue);
                        box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.2);
                    }
                    .search-icon {
                        position: absolute;
                        left: 14px;
                        top: 50%;
                        transform: translateY(-50%);
                        color: var(--text-muted);
                        pointer-events: none;
                    }
                    .filter-tabs {
                        display: flex;
                        gap: 8px;
                        flex-wrap: wrap;
                    }
                    .filter-tab {
                        padding: 8px 16px;
                        background: var(--bg-card);
                        border: 1px solid var(--border);
                        border-radius: 8px;
                        color: var(--text-muted);
                        font-size: 0.85rem;
                        font-weight: 600;
                        cursor: pointer;
                        transition: all 0.2s ease;
                    }
                    .filter-tab:hover, .filter-tab.active {
                        background: rgba(59, 130, 246, 0.15);
                        color: #60a5fa;
                        border-color: rgba(59, 130, 246, 0.4);
                    }
                    .grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
                        gap: 20px;
                    }
                    .card {
                        background: var(--bg-card);
                        border: 1px solid var(--border);
                        border-radius: var(--radius);
                        padding: 24px;
                        display: flex;
                        flex-direction: column;
                        text-decoration: none;
                        color: inherit;
                        transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
                        position: relative;
                        overflow: hidden;
                    }
                    .card:hover {
                        background: var(--bg-card-hover);
                        border-color: var(--border-hover);
                        transform: translateY(-3px);
                        box-shadow: 0 12px 30px rgba(0, 0, 0, 0.4), 0 0 20px rgba(59, 130, 246, 0.15);
                    }
                    .card-header {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        margin-bottom: 16px;
                    }
                    .card-icon {
                        font-size: 1.75rem;
                        width: 44px;
                        height: 44px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        background: rgba(255, 255, 255, 0.04);
                        border-radius: 10px;
                        border: 1px solid rgba(255, 255, 255, 0.06);
                    }
                    .badge {
                        font-size: 0.72rem;
                        font-weight: 700;
                        text-transform: uppercase;
                        letter-spacing: 0.05em;
                        padding: 4px 10px;
                        border-radius: 6px;
                    }
                    .badge-Foundation { background: rgba(59, 130, 246, 0.15); color: #60a5fa; border: 1px solid rgba(59, 130, 246, 0.3); }
                    .badge-Infrastructure { background: rgba(16, 185, 129, 0.15); color: #34d399; border: 1px solid rgba(16, 185, 129, 0.3); }
                    .badge-Gameplay { background: rgba(245, 158, 11, 0.15); color: #fbbf24; border: 1px solid rgba(245, 158, 11, 0.3); }
                    .badge-Operations { background: rgba(168, 85, 247, 0.15); color: #c084fc; border: 1px solid rgba(168, 85, 247, 0.3); }
                    .card-title {
                        font-size: 1.25rem;
                        font-weight: 700;
                        font-family: 'JetBrains Mono', monospace;
                        color: #ffffff;
                        margin-bottom: 8px;
                    }
                    .card-desc {
                        font-size: 0.92rem;
                        color: var(--text-muted);
                        flex-grow: 1;
                        margin-bottom: 20px;
                    }
                    .card-footer {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        font-size: 0.85rem;
                        font-weight: 600;
                        color: #60a5fa;
                        padding-top: 14px;
                        border-top: 1px solid rgba(255, 255, 255, 0.06);
                    }
                    .arrow {
                        transition: transform 0.2s ease;
                    }
                    .card:hover .arrow {
                        transform: translateX(4px);
                    }
                    footer {
                        margin-top: auto;
                        padding: 40px 24px;
                        text-align: center;
                        font-size: 0.88rem;
                        color: var(--text-muted);
                        border-top: 1px solid var(--border);
                    }
                    footer a {
                        color: #60a5fa;
                        text-decoration: none;
                    }
                    footer a:hover {
                        text-decoration: underline;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <header>
                        <div class="hero-tag">✨ Cotani Framework v1.1.0 · Java 25 · Paper 26.2</div>
                        <h1>Cotani API Documentation</h1>
                        <p class="subtitle">Official API javadocs for modular Paper & Folia plugin architecture with non-blocking execution and clear contracts.</p>
                        <div class="nav-links">
                            <a href="https://github.com/HanielCota/Cotani" class="nav-btn nav-btn-primary" target="_blank">
                                <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z"/></svg>
                                GitHub Repository
                            </a>
                            <a href="https://github.com/HanielCota/Cotani/blob/master/docs/ai/cotani-cookbook.md" class="nav-btn nav-btn-secondary" target="_blank">
                                📖 Cookbook Recipes
                            </a>
                            <a href="https://github.com/HanielCota/Cotani/blob/master/docs/architecture.md" class="nav-btn nav-btn-secondary" target="_blank">
                                🏗️ Architecture Guide
                            </a>
                        </div>
                    </header>

                    <div class="controls">
                        <div class="search-bar">
                            <svg class="search-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
                            <input type="text" id="searchInput" placeholder="Filter modules (e.g. storage, task, economy)..." oninput="filterCards()">
                        </div>
                        <div class="filter-tabs">
                            <button class="filter-tab active" onclick="setCategory('all', this)">All Modules</button>
                            <button class="filter-tab" onclick="setCategory('Foundation', this)">Foundation</button>
                            <button class="filter-tab" onclick="setCategory('Infrastructure', this)">Infrastructure</button>
                            <button class="filter-tab" onclick="setCategory('Gameplay', this)">Gameplay</button>
                            <button class="filter-tab" onclick="setCategory('Operations', this)">Operations</button>
                        </div>
                    </div>

                    <div class="grid" id="modulesGrid">
                        $cards
                    </div>
                </div>

                <footer>
                    <p>Cotani Framework · Open source under the MIT License · Maintained by <a href="https://github.com/HanielCota" target="_blank">Haniel Cota</a></p>
                </footer>

                <script>
                    let currentCategory = 'all';

                    function setCategory(cat, el) {
                        currentCategory = cat;
                        document.querySelectorAll('.filter-tab').forEach(t => t.classList.remove('active'));
                        el.classList.add('active');
                        filterCards();
                    }

                    function filterCards() {
                        const q = document.getElementById('searchInput').value.toLowerCase().trim();
                        const cards = document.querySelectorAll('.card');
                        cards.forEach(card => {
                            const cat = card.getAttribute('data-category');
                            const text = card.getAttribute('data-name').toLowerCase();
                            const matchCat = currentCategory === 'all' || cat === currentCategory;
                            const matchText = !q || text.includes(q);
                            card.style.display = (matchCat && matchText) ? 'flex' : 'none';
                        });
                    }
                </script>
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
