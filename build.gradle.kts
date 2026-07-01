import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
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

