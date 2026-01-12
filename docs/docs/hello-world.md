---
sidebar_position: 2
---

# Hello World

From zero to a working search API in 5 minutes.

---

## What We're Building

A REST API that lets you search users with:
- Dynamic filtering (by name, email, age...)
- Criteria combination (AND, OR, NOT)
- Projection (choose which fields to return)
- Pagination

**End result:** A `POST /api/users/search` endpoint that accepts JSON requests.

---

## Step 1: Dependencies

Add FilterQL to your Spring Boot project.

**Maven** (`pom.xml`):

```xml
<dependencies>
    <!-- FilterQL Spring Starter -->
    <dependency>
        <groupId>io.github.cyfko</groupId>
        <artifactId>filterql-spring-starter</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- Your existing Spring dependencies -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

**Gradle** (`build.gradle.kts`):

```kotlin
dependencies {
    implementation("io.github.cyfko:filterql-spring-starter:1.0.0")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
}
```

---

## Step 2: Your JPA Entity

You probably already have an entity. Let's use a simple example:

```java
package com.example.demo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    
    private String email;
    
    private Integer age;
    
    private String status;
    
    // Getters and setters...
}
```

---

## Step 3: Create the DTO with Exposure

This is **THE** FilterQL part. You declare a DTO that defines:
- Which fields are returned
- Which fields are filterable and with which operators

```java
package com.example.demo.dto;

import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.spring.ExposedAs;
import io.github.cyfko.filterql.spring.Exposure;
import io.github.cyfko.projection.Projection;
import com.example.demo.entity.User;

@Projection(from = User.class)
@Exposure(value = "users", basePath = "/api")
public class UserDTO {

    private Long id;

    @ExposedAs(value = "NAME", operators = {Op.EQ, Op.NE, Op.MATCHES, Op.IN})
    private String name;

    @ExposedAs(value = "EMAIL", operators = {Op.EQ, Op.NE, Op.MATCHES})
    private String email;

    @ExposedAs(value = "AGE", operators = {Op.EQ, Op.GT, Op.GTE, Op.LT, Op.LTE, Op.RANGE})
    private Integer age;

    @ExposedAs(value = "STATUS", operators = {Op.EQ, Op.NE, Op.IN})
    private String status;

    // Getters...
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public Integer getAge() { return age; }
    public String getStatus() { return status; }
}
```

**What this means:**
- `@Projection(from = User.class)`: This DTO represents the `User` entity
- `@Exposure(value = "users", basePath = "/api")`: Generates `POST /api/users/search`
- `@ExposedAs(value = "NAME", operators = {...})`: This field is filterable with these operators

:::tip Custom Mapping
If the DTO field name differs from the entity, use `@Projected`:
```java
@Projected(from = "orderNumber")  // Entity: orderNumber → DTO: number
private String number;
```
See the [Advanced Guide](./advanced-guide#projections) for more details.
:::

---

## Step 4: Run the Application

```bash
./mvnw spring-boot:run
```

**That's it.** The endpoint now exists.

---

## Step 5: Test It

### Simple Request (single filter)

```bash
curl -X POST http://localhost:8080/api/users/search \
  -H "Content-Type: application/json" \
  -d '{
    "filters": {
      "f1": { "ref": "NAME", "op": "MATCHES", "value": "john" }
    },
    "combineWith": "f1"
  }'
```

**Result:** All users whose name contains "john".

---

### Combined Request (multiple filters)

```bash
curl -X POST http://localhost:8080/api/users/search \
  -H "Content-Type: application/json" \
  -d '{
    "filters": {
      "byName": { "ref": "NAME", "op": "MATCHES", "value": "john" },
      "isAdult": { "ref": "AGE", "op": "GTE", "value": 18 },
      "isActive": { "ref": "STATUS", "op": "EQ", "value": "active" }
    },
    "combineWith": "byName & isAdult & isActive"
  }'
```

**Result:** Users whose name contains "john", who are 18 or older, and who are active.

---

### With Projection and Pagination

```bash
curl -X POST http://localhost:8080/api/users/search \
  -H "Content-Type: application/json" \
  -d '{
    "filters": {
      "adults": { "ref": "AGE", "op": "GTE", "value": 18 }
    },
    "combineWith": "adults",
    "projection": ["name", "email"],
    "pagination": { "page": 0, "size": 10 }
  }'
```

**Result:**

```json
{
  "content": [
    { "name": "Alice Martin", "email": "alice@example.com" },
    { "name": "Bob Wilson", "email": "bob@example.com" }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 42,
  "totalPages": 5
}
```

---

## Summary

| What you did | Time |
|--------------|------|
| Added a Maven dependency | 30 sec |
| Created an annotated DTO (~20 lines) | 2 min |
| Ran the application | 30 sec |

**What is auto-generated at compile time:**
- The `UserDTO_ implements PropertyReference` enum (DTO name + underscore `_`)
- The REST controller with the `/api/users/search` endpoint
- The operator validation logic
- The JPA mapping

| What you got |
|--------------|
| REST search endpoint |
| Dynamic filtering on 4 properties |
| 7 different operators |
| Arbitrary boolean combination |
| Field projection |
| Built-in pagination |

---

## What's Next?

You now master the basic flow. To go further:

| Goal | Guide |
|------|-------|
| Understand all operators, DSL syntax, projection | [→ Essential Guide](./essential-guide) |
| Relations, custom operators, advanced JPA mapping | [→ Advanced Guide](./advanced-guide) |
| Complete technical reference | [→ API Reference](./reference/core) |
