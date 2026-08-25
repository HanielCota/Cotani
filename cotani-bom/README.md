<div align="center">

<img src="../logo.png" alt="Cotani logo" width="220">

# Cotani BOM

</div>

Bill of Materials (BOM) for dependency alignment across all Cotani modules.

## Gradle

```kotlin
dependencies {
    implementation(platform("com.github.HanielCota.Cotani:cotani-bom:v1.1.1"))

    implementation("com.github.HanielCota.Cotani:cotani-core")
    implementation("com.github.HanielCota.Cotani:cotani-task")
    implementation("com.github.HanielCota.Cotani:cotani-gui")
    implementation("com.github.HanielCota.Cotani:cotani-user")
    implementation("com.github.HanielCota.Cotani:cotani-economy")
}
```

For a local checkout, run `./gradlew publishToMavenLocal` and replace the `com.github.HanielCota.Cotani` group with
`com.cotani`.

## Maven

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.github.HanielCota.Cotani</groupId>
            <artifactId>cotani-bom</artifactId>
            <version>v1.1.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```
