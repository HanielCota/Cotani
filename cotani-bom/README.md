# Cotani BOM

Bill of Materials (BOM) for dependency alignment across all Cotani modules.

## Gradle

```kotlin
dependencies {
    implementation(platform("com.cotani:cotani-bom:1.1.0"))

    implementation("com.cotani:cotani-core")
    implementation("com.cotani:cotani-task")
    implementation("com.cotani:cotani-gui")
    implementation("com.cotani:cotani-user")
    implementation("com.cotani:cotani-economy")
}
```

## Maven

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.cotani</groupId>
            <artifactId>cotani-bom</artifactId>
            <version>1.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```
