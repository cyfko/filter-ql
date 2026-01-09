# Changelog

All notable changes to the FilterQL Spring Boot Starter will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [4.0.0] - 2025-10-22

### 🚨 BREAKING CHANGES

This is a **complete architectural rewrite** of `filterql-spring`. The artifact has been transformed from a basic Spring adapter into a full-featured **Spring Boot Starter** with annotation-driven code generation.

#### Migration Required

**Version 3.x.x (Legacy - Spring Adapter)**
```xml
<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>filterql-spring</artifactId>
    <version>3.1.1</version>
</dependency>
```

**Version 4.0.0+ (New - Spring Boot Starter)**
```xml
<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>filterql-spring</artifactId>
    <version>4.0.0</version>
</dependency>
```

#### What Changed

**Mapper Method Naming:**
- The mapper method has been renamed from `toDto()` to `map()` for consistency
- This aligns with Spring's `Page.map()` and `PagedResultMapper.map()` naming conventions

```java
// Before (v3.x)
public class UserMapper {
    public static UserDTO toDto(User user) { ... }
}

// After (v4.0)
public class UserMapper {
    public static UserDTO map(User user) { ... }  // ✨ Renamed to 'map'
}
```

**Architecture:**

**Before (v3.x):**
- Manual Spring configuration required
- Manual controller creation
- Manual repository integration
- Limited features

**After (v4.0):**
- ✨ **Annotation-driven**: Use `@Filtered` on entities to auto-generate REST endpoints
- ✨ **Zero boilerplate**: No manual controller/service creation needed
- ✨ **Custom result mappers**: Transform `Page<T>` responses to your API format
- ✨ **Virtual fields**: Add computed/derived filterable fields
- ✨ **Endpoint customization**: Apply security, caching, custom annotations declaratively
- ✨ **Type-safe**: Full compile-time validation with annotation processing

#### Migration Guide

See [docs/MIGRATION_v3_to_v4.md](docs/MIGRATION_v3_to_v4.md) for detailed migration instructions.

### ✨ New Features

#### 1. Annotation-Driven REST API Generation

Annotate your JPA entities to automatically generate complete FilterQL REST endpoints:

```java
@Entity
@Filtered(
    mapperClass = UserMapper.class,
    exposure = @EndpointExposure(
        value = "users",
        basePath = "/api/v1"
    )
)
public class User {
    @FilterField
    private String email;

    @FilterField
    private String name;
}
```

**Generated Endpoints:**
- `POST /api/v1/search/users` - Search with filters
- `POST /api/v1/count/users` - Count matching records (optional)
- `POST /api/v1/exists/users` - Check existence (optional)

#### 2. Custom Result Mappers (PagedResultMapper)

Transform Spring Data `Page<T>` into your custom API response format:

```java
@Component
public class UserResultMapper implements PagedResultMapper<UserDTO, ApiResponse<UserDTO>> {
    @Override
    public ApiResponse<UserDTO> map(Page<UserDTO> page) {
        return ApiResponse.<UserDTO>builder()
            .data(page.getContent())
            .pagination(PaginationMeta.of(page))
            .status("success")
            .timestamp(Instant.now())
            .build();
    }
}

// Use in entity
@EndpointExposure(
    value = "users",
    resultMapper = UserResultMapper.class
)
```

**Response:**
```json
{
  "data": [...],
  "pagination": {
    "currentPage": 0,
    "totalPages": 5,
    "totalElements": 100
  },
  "status": "success",
  "timestamp": "2025-10-22T10:30:00Z"
}
```

See [docs/PAGED_RESULT_MAPPER.md](docs/PAGED_RESULT_MAPPER.md) for complete documentation.

#### 3. Virtual Field Resolvers

Add computed/derived fields that don't exist in your database:

```java
@Entity
@Filtered(
    mapperClass = ProductMapper.class,
    virtualResolvers = {PriceCalculator.class}
)
public class Product {
    @FilterField
    private BigDecimal basePrice;

    @VirtualField(referredAs = "DISCOUNTED_PRICE", type = BigDecimal.class)
    public static PredicateResolverMapping<Product> discountedPrice() {
        return () -> (root, query, cb) ->
            cb.multiply(root.get("basePrice"), 0.9);
    }
}
```

**Usage:**
```json
{
  "filters": [
    {
      "field": "DISCOUNTED_PRICE",
      "operator": "LESS_THAN",
      "value": "50.00"
    }
  ]
}
```

#### 4. Endpoint Annotation Customization

Apply Spring Security, caching, and custom annotations declaratively:

```java
@EndpointExposure(
    value = "orders",
    annotations = {
        @EndpointAnnotation(
            annotationClass = PreAuthorize.class,
            attributes = @AnnotationAttribute(
                name = "value",
                value = "hasRole('ADMIN')"
            )
        ),
        @EndpointAnnotation(
            annotationClass = Cacheable.class,
            attributes = {
                @AnnotationAttribute(name = "value", value = "orderSearchCache"),
                @AnnotationAttribute(name = "key", value = "#filterRequest.hashCode()")
            },
            targetEndpoints = {EndpointType.SEARCH}
        )
    }
)
```

**Generated:**
```java
@PreAuthorize("hasRole('ADMIN')")
@Cacheable(value = "orderSearchCache", key = "#filterRequest.hashCode()")
@PostMapping("/api/orders/search")
public Page<OrderDTO> searchOrders(...) { ... }
```

#### 5. Schema Introspection Endpoint

Automatically generated schema endpoint provides filterable field metadata:

```
GET /api/v1/schema/users
```

**Response:**
```json
{
  "entityName": "User",
  "fields": [
    {
      "name": "email",
      "type": "String",
      "operators": ["EQUALS", "CONTAINS", "STARTS_WITH"],
      "nullable": false
    },
    {
      "name": "age",
      "type": "Integer",
      "operators": ["EQUALS", "GREATER_THAN", "LESS_THAN"],
      "nullable": true
    }
  ]
}
```

### 🔧 Technical Improvements

- **Annotation Processing**: Compile-time code generation with full type safety
- **Spring Boot Auto-Configuration**: Automatic service/repository discovery
- **Template Engine**: Flexible code generation with customizable templates
- **Generic Type Preservation**: Full support for parameterized types in generated code
- **Constructor Injection**: Proper Spring dependency injection for all generated components

### 📦 Dependencies

- **Minimum Spring Boot Version**: 3.3.5
- **Minimum Java Version**: 17
- **FilterQL Adapter JPA**: 2.0.0
- **FilterQL Core**: 3.2.0 (implied via adapter-jpa)

### 📚 Documentation

- [Getting Started Guide](README.md)
- [PagedResultMapper Documentation](docs/PAGED_RESULT_MAPPER.md)
- [Migration Guide v3 → v4](docs/MIGRATION_v3_to_v4.md)
- [API Reference](docs/API_REFERENCE.md)

### 🙏 Acknowledgments

This release represents a complete reimagining of the FilterQL Spring integration, designed to provide the best developer experience for building dynamic filtering APIs with Spring Boot.

---

## [3.1.1] - Previous Releases

See legacy documentation for version 3.x.x changes (basic Spring adapter).

---

**Note:** Versions 3.x.x and below used a different architecture (basic Spring adapter) and are now considered legacy. Please migrate to 4.0.0+ for the modern Spring Boot Starter experience.
