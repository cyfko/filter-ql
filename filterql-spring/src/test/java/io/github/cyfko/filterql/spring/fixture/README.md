# FilterQL Integration Test Entities

This package contains test entities for integration testing of the FilterQL Spring Boot Starter.

## Test Entities

### TestEntity
A simple JPA entity with `@Filtered` annotation for testing:
- PropertyRef enum generation
- FilterContext bean registration
- Basic filtering functionality

**Fields:**
- `id` (Long) - Primary key
- `name` (String) - Filterable
- `age` (Integer) - Filterable
- `active` (Boolean) - Filterable

## Generated Classes

When the annotation processor runs during `mvn test-compile`, the following classes are generated:

1. **TestEntityPropertyRef.java** - Enum containing constants for each filterable field
2. **TestEntityFilterQLConfiguration.java** - Spring configuration with FilterContext bean
3. **FieldMetadataRegistryImpl.java** - Registry implementation (if not already generated)

## Running Tests

### Prerequisites
The annotation processor must run during test compilation for these tests to pass.

### Maven Configuration
Ensure your `pom.xml` has:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.github.cyfko</groupId>
                        <artifactId>filterql-spring-boot-starter</artifactId>
                        <version>${project.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### Running the Tests

```bash
# Compile with annotation processing
mvn clean test-compile

# Run integration tests
mvn test -Dtest=ComprehensiveIntegrationTest

# Or run all tests
mvn clean test
```

## Verifying Generated Code

After compilation, check for generated files:

```bash
# Linux/Mac
find target/generated-test-sources -name "*TestEntity*"

# Windows
dir /s /b target\generated-test-sources\*TestEntity*
```

Expected generated files:
- `TestEntityPropertyRef.java`
- `TestEntityFilterQLConfiguration.java`

## Troubleshooting

### PropertyRef enum not found
**Cause:** Annotation processor didn't run during test compilation.

**Solution:**
1. Verify annotation processor is in `annotationProcessorPaths`
2. Run `mvn clean test-compile` (not just `mvn compile`)
3. Check `target/generated-test-sources/annotations/` for generated files

### FilterContext bean not found
**Cause:** Spring Boot auto-configuration not active or bean not scanned.

**Solution:**
1. Verify `@SpringBootTest` is present on test class
2. Check that `TestConfig` enables component scanning
3. Ensure generated configuration is on test classpath

### Tests are @Disabled
**Cause:** Integration tests require full infrastructure (DB, Spring context).

**Solution:**
1. Configure H2 test database in `application-test.properties`
2. Ensure all dependencies are available
3. Remove `@Disabled` annotation from test class
