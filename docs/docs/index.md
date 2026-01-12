---
slug: /
sidebar_position: 1
---

# FilterQL

## The Problem You Have

You're building a REST API with Spring Boot. You have a `User` entity:

```java
@Entity
public class User {
    private Long id;
    private String name;
    private String email;
    private Integer age;
    private String status;
    private List<Book> books;
}
```

Your client (web app, mobile) wants to:
- Search users whose name contains "john"
- Filter those who are over 25 years old
- Only retrieve `name` and `email` (not the whole object)
- Paginate results (page 1, 20 per page)

### What You Do Today

**Option 1: Multiple endpoints**
```java
@GetMapping("/users/by-name/{name}")
@GetMapping("/users/by-age-greater-than/{age}")
@GetMapping("/users/by-name-and-age/{name}/{age}")
// ... and 20 other combinations
```

**Option 2: Query string parameters**
```java
@GetMapping("/users")
public List<User> search(
    @RequestParam(required = false) String name,
    @RequestParam(required = false) Integer minAge,
    @RequestParam(required = false) Integer maxAge,
    // ... 15 other parameters
) {
    // 200 lines of if/else to build the query
}
```

**Both are a maintenance nightmare.**

---

## The FilterQL Solution

One endpoint. One JSON request. The client decides what to filter.

### HTTP Request

```http
POST /api/users/search
Content-Type: application/json

{
  "filters": {
    "byName": { "ref": "NAME", "op": "MATCHES", "value": "john" },
    "isAdult": { "ref": "AGE", "op": "GTE", "value": 25 }
  },
  "combineWith": "byName & isAdult",
  "projection": ["name", "email"],
  "pagination": { "page": 0, "size": 20 }
}
```

### Response

```json
{
  "content": [
    { "name": "John Doe", "email": "john.doe@example.com" },
    { "name": "Johnny Smith", "email": "johnny@example.com" }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1
}
```

### What You Wrote Server-Side

```java
@Projection(from = User.class)
@Exposure(value = "users", basePath = "/api")
public class UserDTO {

    @ExposedAs(value = "NAME", operators = {Op.EQ, Op.MATCHES})
    private String name;

    @ExposedAs(value = "EMAIL", operators = {Op.EQ, Op.MATCHES})
    private String email;

    @ExposedAs(value = "AGE", operators = {Op.EQ, Op.GT, Op.GTE, Op.LT, Op.LTE})
    private Integer age;

    @ExposedAs(value = "STATUS", operators = {Op.EQ, Op.IN})
    private String status;

    // Getters...
}
```

**That's it.** The `POST /api/users/search` endpoint is generated automatically.

---

## How It Works

```
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│  Web Client  │      │   FilterQL   │      │   Database   │
│  sends JSON  │─────▶│  translates  │─────▶│              │
└──────────────┘      └──────────────┘      └──────────────┘
                             │
                             ▼
                      ┌──────────────┐
                      │    JSON      │
                      │   Response   │
                      └──────────────┘
```

1. **The client** sends what it wants (filters, fields, pagination)
2. **FilterQL** validates, parses, translates to JPA query
3. **The database** executes
4. **The client** receives exactly what it asked for

---

## Ready?

| Your Goal | Start Here |
|-----------|------------|
| See a complete working example | [→ Hello World](./hello-world) |
| Understand the basics quickly | [→ Essential Guide](./essential-guide) |
| Advanced cases and customization | [→ Advanced Guide](./advanced-guide) |
| Complete technical documentation | [→ API Reference](./reference/core) |

---

## Quick Installation

**Maven (Spring Boot)**:

```xml
<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>filterql-spring-starter</artifactId>
    <version>4.0.0</version>
</dependency>
```

**Gradle**:

```kotlin
implementation("io.github.cyfko:filterql-spring-starter:4.0.0")
```

That's all you need to get started. [→ Let's go to Hello World](./hello-world)
