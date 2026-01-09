# FilterQL Spring Boot Starter

**Version:** 4.0.0  
**Type:** POM (Dependency Aggregator)

Convenience dependency aggregator for FilterQL Spring Boot integration. Simplifies dependency management by providing all required FilterQL dependencies with tested compatible versions.

---

## Table of Contents

1. [Purpose](#purpose)
2. [Usage](#usage)
3. [Included Dependencies](#included-dependencies)
4. [Dependency Tree](#dependency-tree)
5. [Version Compatibility](#version-compatibility)
6. [Migration Guide](#migration-guide)
7. [Troubleshooting](#troubleshooting)

---

## Purpose

This starter module serves as a **Bill of Materials (BOM)** for FilterQL Spring Boot projects, providing:

- **Single Dependency**: One dependency imports all FilterQL components
- **Version Consistency**: Pre-tested compatible versions across all modules
- **Simplified Upgrades**: Update one version number to upgrade all FilterQL dependencies
- **Reduced Configuration**: No need to manage individual version numbers

**Use this starter when:**

- Starting a new Spring Boot project with FilterQL
- You want automatic version management
- You need all FilterQL features (Core + JPA + Spring + Annotation Processing)

**Use individual dependencies when:**

- You only need specific modules (e.g., Core + JPA without Spring integration)
- You need to override specific module versions
- You're integrating FilterQL into an existing project with complex dependency constraints

---

## Usage

### Maven

**Add to `pom.xml`:**

```xml
<dependencies>
    <dependency>
        <groupId>io.github.cyfko</groupId>
        <artifactId>filterql-spring-starter</artifactId>
        <version>4.0.0</version>
        <type>pom</type>
    </dependency>
</dependencies>
```

**Complete Example:**

```xml
<project>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
    </parent>
    
    <dependencies>
        <!-- FilterQL Starter -->
        <dependency>
            <groupId>io.github.cyfko</groupId>
            <artifactId>filterql-spring-starter</artifactId>
            <version>4.0.0</version>
            <type>pom</type>
        </dependency>
        
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        
        <!-- Database Driver (choose one) -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
    </dependencies>
</project>
```

### Gradle (Kotlin DSL)

**Add to `build.gradle.kts`:**

```kotlin
dependencies {
    implementation(platform("io.github.cyfko:filterql-spring-starter:4.0.0"))
    
    // Spring Boot dependencies
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    
    // Database driver
    runtimeOnly("com.h2database:h2")
}
```

### Gradle (Groovy DSL)

**Add to `build.gradle`:**

```groovy
dependencies {
    implementation platform("io.github.cyfko:filterql-spring-starter:4.0.0")
    
    implementation "org.springframework.boot:spring-boot-starter-data-jpa"
    implementation "org.springframework.boot:spring-boot-starter-web"
    
    runtimeOnly "com.h2database:h2"
}
```

---

## Included Dependencies

This starter transitively includes the following FilterQL modules:

### 1. **filterql-spring** (4.0.0)

**Provides:**
- Spring Boot auto-configuration
- Annotation processor for `@Exposure` and `@ExposedAs` (processes external `@Projection` from [projection-spec](https://github.com/cyfko/projection-spec))
- PropertyRef enum code generation
- REST controller scaffolding
- `FilterQlService` interface
- `PaginatedData` wrapper
- Security integration (row-level filtering)

**External Dependencies:**
- Requires [projection-metamodel-processor](https://github.com/cyfko/jpa-metamodel-processor) for `@Projection` annotation

**Documentation:** [FilterQL Spring README](../filterql-spring/README.md)

### 2. **filterql-adapter-jpa** (2.0.0)

**Provides:**
- JPA Criteria API integration
- `JpaFilterContext` implementation
- Custom predicate resolvers
- DTO projection strategies (FullEntity, MultiQuery)
- Computed field support
- Path resolution utilities

**Documentation:** [FilterQL JPA Adapter README](../filterql-jpa/README.md)

### 3. **filterql-core** (4.0.0) *(Transitive)*

**Provides:**
- DSL parser (`BasicDslParser`)
- Filter tree model (`FilterTree`, `FilterNode`)
- Operator registry (`Op` enum with 14 operators)
- Validation (`PropertyReference`, `OperatorValidationUtils`)
- Caching (`BoundedLRUCache`, `StructuralNormalizer`)
- SPI contracts (`FilterContext`, `PredicateResolver`, `ExecutionStrategy`)

**Documentation:** [FilterQL Core README](../../core/java/README.md)

### 4. **projection-metamodel-processor** (1.0.0) *(External Dependency)*

**Source:** [projection-metamodel-processor](https://github.com/cyfko/jpa-metamodel-processor) implements [projection-spec](https://github.com/cyfko/projection-spec)

**Provides:**
- `@Projection`, `@Provider`, `@Projected`, `@Computed` annotations
- JPA entity metadata extraction
- `PersistenceRegistry` and `ProjectionRegistry` generation
- Type-safe DTO projection definitions

**Note:** This is an **external library** maintained separately from FilterQL. Required by filterql-spring for annotation processing.

---

## Dependency Tree

```
filterql-spring-starter:4.0.0 (pom)
├── filterql-spring:4.0.0
│   ├── filterql-adapter-jpa:2.0.0 (provided scope)
│   │   └── filterql-core:4.0.0
│   ├── spring-boot-starter-data-jpa:3.3.5
│   ├── spring-boot-starter-web:3.3.5
│   └── spring-context:6.1.x
├── filterql-adapter-jpa:2.0.0
│   └── filterql-core:4.0.0
└── projection-metamodel-processor:1.0.0 (EXTERNAL)
    └── projection-spec:1.0.0 (EXTERNAL - annotations only)
```

**Total Transitive Dependencies:**

- **FilterQL Core**: 4.0.0
- **FilterQL JPA**: 2.0.0
- **FilterQL Spring**: 4.0.0
- **Projection Processor**: 1.0.0
- Spring Boot dependencies (managed by Spring Boot BOM)

---

## Version Compatibility

### Tested Configurations

| FilterQL Starter | Spring Boot | Java | JPA  |
|------------------|-------------|------|------|
| 4.0.0            | 3.3.5+      | 17+  | 3.1+ |
| 3.x.x            | 3.2.x       | 17+  | 3.1+ |
| 2.x.x            | 3.0.x       | 17+  | 3.0+ |

### Spring Boot Version Matrix

| Spring Boot | Spring Framework | Hibernate | Java |
|-------------|------------------|-----------|------|
| 3.3.5       | 6.1.x            | 6.4.x     | 17+  |
| 3.2.x       | 6.1.x            | 6.4.x     | 17+  |
| 3.0.x       | 6.0.x            | 6.1.x     | 17+  |

**Important Notes:**

- **Java 17+** is required (Spring Boot 3.x baseline)
- **Hibernate 6.x** is required for JPA 3.1 support
- **Spring Boot 2.x** is not supported (FilterQL requires Spring Boot 3.x)

---

## Migration Guide

### From Individual Dependencies to Starter

**Before:**

```xml
<dependencies>
    <dependency>
        <groupId>io.github.cyfko</groupId>
        <artifactId>filterql-core</artifactId>
        <version>4.0.0</version>
    </dependency>
    <dependency>
        <groupId>io.github.cyfko</groupId>
        <artifactId>filterql-adapter-jpa</artifactId>
        <version>2.0.0</version>
    </dependency>
    <dependency>
        <groupId>io.github.cyfko</groupId>
        <artifactId>filterql-spring</artifactId>
        <version>4.0.0</version>
    </dependency>
</dependencies>
```

**After:**

```xml
<dependencies>
    <dependency>
        <groupId>io.github.cyfko</groupId>
        <artifactId>filterql-spring-starter</artifactId>
        <version>4.0.0</version>
        <type>pom</type>
    </dependency>
</dependencies>
```

**Benefits:**

- **Reduced Lines**: 3 dependencies → 1 dependency
- **Version Consistency**: No risk of incompatible versions
- **Automatic Updates**: Update one version to upgrade all modules

### Upgrading FilterQL Version

**Step 1:** Update starter version in `pom.xml`:

```xml
<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>filterql-spring-starter</artifactId>
    <version>4.1.0</version> <!-- Updated -->
    <type>pom</type>
</dependency>
```

**Step 2:** Clean and rebuild:

```bash
mvn clean compile
```

**Step 3:** Verify generated sources:

```bash
ls target/generated-sources/annotations/
```

---

## Troubleshooting

### Issue: Dependency Convergence Conflicts

**Symptom:** Maven warns about conflicting dependency versions

**Solution 1: Use Dependency Management**

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.cyfko</groupId>
            <artifactId>filterql-spring-starter</artifactId>
            <version>4.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.cyfko</groupId>
        <artifactId>filterql-spring</artifactId>
    </dependency>
</dependencies>
```

**Solution 2: Exclude Transitive Dependencies**

```xml
<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>filterql-spring-starter</artifactId>
    <version>4.0.0</version>
    <type>pom</type>
    <exclusions>
        <exclusion>
            <groupId>*</groupId>
            <artifactId>*</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<!-- Then manually add required dependencies -->
```

### Issue: Annotation Processor Not Running

**Symptom:** No generated sources in `target/generated-sources/`

**Verification:**

```bash
mvn dependency:tree | grep projection-metamodel-processor
```

**Solution:**

Ensure annotation processing is enabled:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <proc>full</proc>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### Issue: Version Conflicts with Spring Boot BOM

**Symptom:** `ClassNotFoundException` or `NoSuchMethodError`

**Root Cause:** Spring Boot BOM overrides FilterQL dependency versions

**Solution: Explicit Version Override**

```xml
<dependencyManagement>
    <dependencies>
        <!-- Spring Boot BOM -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>3.3.5</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        
        <!-- FilterQL BOM (higher priority) -->
        <dependency>
            <groupId>io.github.cyfko</groupId>
            <artifactId>filterql-spring-starter</artifactId>
            <version>4.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Issue: Need Specific Module Version

**Scenario:** You need filterql-jpa 2.1.0 but starter provides 2.0.0

**Solution: Override Specific Dependency**

```xml
<dependencies>
    <!-- Starter (imports 2.0.0) -->
    <dependency>
        <groupId>io.github.cyfko</groupId>
        <artifactId>filterql-spring-starter</artifactId>
        <version>4.0.0</version>
        <type>pom</type>
    </dependency>
    
    <!-- Override JPA version -->
    <dependency>
        <groupId>io.github.cyfko</groupId>
        <artifactId>filterql-adapter-jpa</artifactId>
        <version>2.1.0</version> <!-- Override -->
    </dependency>
</dependencies>
```

---

## Alternative: Manual Dependency Management

If you need fine-grained control over individual versions:

```xml
<dependencies>
    <!-- Core (required) -->
    <dependency>
        <groupId>io.github.cyfko</groupId>
        <artifactId>filterql-core</artifactId>
        <version>4.0.0</version>
    </dependency>
    
    <!-- JPA Adapter (optional, for JPA integration) -->
    <dependency>
        <groupId>io.github.cyfko</groupId>
        <artifactId>filterql-adapter-jpa</artifactId>
        <version>2.0.0</version>
    </dependency>
    
    <!-- Spring Integration (optional, for Spring Boot) -->
    <dependency>
        <groupId>io.github.cyfko</groupId>
        <artifactId>filterql-spring</artifactId>
        <version>4.0.0</version>
    </dependency>
    
    <!-- Annotation Processor (optional, for @Projection) -->
    <dependency>
        <groupId>io.github.cyfko</groupId>
        <artifactId>projection-metamodel-processor</artifactId>
        <version>1.0.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

---

## See Also

**FilterQL Modules:**
- [FilterQL Spring Documentation](../filterql-spring/README.md) - Spring Boot integration
- [FilterQL JPA Documentation](../filterql-jpa/README.md) - JPA Criteria API adapter
- [FilterQL Core Documentation](../../core/java/README.md) - Core DSL and parsing
- [Main README](../../README.md) - Project overview

**External Dependencies:**
- [Projection Specification](https://github.com/cyfko/projection-spec) - Annotation specification for DTO projections
- [Projection Metamodel Processor](https://github.com/cyfko/jpa-metamodel-processor) - Annotation processor implementation
- [Maven Central: projection-metamodel-processor](https://search.maven.org/artifact/io.github.cyfko/jpa-metamodel-processor)

---

## License

Licensed under the MIT License. See [LICENSE](../../LICENSE) for details.
