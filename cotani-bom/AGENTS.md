# cotani-bom

## Scope

Bill of Materials (BOM) for dependency alignment across all Cotani modules in Gradle and Maven projects.

## Hard rules

1. All published library modules must be listed as API constraints in `cotani-bom/build.gradle.kts`.
2. Do not include example or non-published subprojects in the BOM.
3. Keep version alignment synchronized across all submodules.

## Patterns

### Gradle platform import

```kotlin
dependencies {
    implementation(platform("com.cotani:cotani-bom:1.1.2"))
    implementation("com.cotani:cotani-core")
    implementation("com.cotani:cotani-task")
    implementation("com.cotani:cotani-gui")
    implementation("com.cotani:cotani-user")
}
```

### Maven BOM import

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.cotani</groupId>
            <artifactId>cotani-bom</artifactId>
            <version>1.1.2</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Anti-patterns

- Specifying explicit module versions when the BOM platform is already imported.
- Adding non-library projects (such as `docs-examples`) to BOM constraints.

## Related skills

- `java-engineering-standards`

