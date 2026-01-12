---
sidebar_position: 3
---

# Essential Guide

Everything you need for 80% of use cases. From simplest to most useful.

---

## 1. Operators

An operator defines **how** to compare a property to a value.

### Comparison Operators

| Operator | Meaning | JSON Example | SQL Equivalent |
|----------|---------|--------------|----------------|
| `EQ` | Equal to | `{"ref": "STATUS", "op": "EQ", "value": "active"}` | `status = 'active'` |
| `NE` | Not equal to | `{"ref": "STATUS", "op": "NE", "value": "deleted"}` | `status != 'deleted'` |
| `GT` | Greater than | `{"ref": "AGE", "op": "GT", "value": 18}` | `age > 18` |
| `GTE` | Greater or equal | `{"ref": "AGE", "op": "GTE", "value": 18}` | `age >= 18` |
| `LT` | Less than | `{"ref": "PRICE", "op": "LT", "value": 100}` | `price < 100` |
| `LTE` | Less or equal | `{"ref": "PRICE", "op": "LTE", "value": 100}` | `price <= 100` |

### Pattern Operators

| Operator | Meaning | JSON Example | SQL Equivalent |
|----------|---------|--------------|----------------|
| `MATCHES` | Contains (case-insensitive) | `{"ref": "NAME", "op": "MATCHES", "value": "john"}` | `LOWER(name) LIKE '%john%'` |
| `NOT_MATCHES` | Does not contain | `{"ref": "NAME", "op": "NOT_MATCHES", "value": "test"}` | `LOWER(name) NOT LIKE '%test%'` |

### List Operators

| Operator | Meaning | JSON Example | SQL Equivalent |
|----------|---------|--------------|----------------|
| `IN` | In the list | `{"ref": "STATUS", "op": "IN", "value": ["active", "pending"]}` | `status IN ('active', 'pending')` |
| `NOT_IN` | Not in the list | `{"ref": "STATUS", "op": "NOT_IN", "value": ["deleted"]}` | `status NOT IN ('deleted')` |

### Range Operators

| Operator | Meaning | JSON Example | SQL Equivalent |
|----------|---------|--------------|----------------|
| `RANGE` | Between min and max (inclusive) | `{"ref": "AGE", "op": "RANGE", "value": [18, 65]}` | `age BETWEEN 18 AND 65` |
| `NOT_RANGE` | Outside the range | `{"ref": "AGE", "op": "NOT_RANGE", "value": [18, 65]}` | `age NOT BETWEEN 18 AND 65` |

### Nullity Operators

| Operator | Meaning | JSON Example | SQL Equivalent |
|----------|---------|--------------|----------------|
| `IS_NULL` | Is null | `{"ref": "EMAIL", "op": "IS_NULL", "value": null}` | `email IS NULL` |
| `NOT_NULL` | Is not null | `{"ref": "EMAIL", "op": "NOT_NULL", "value": null}` | `email IS NOT NULL` |

---

## 2. Combining Filters

The `combineWith` field contains a boolean expression that combines your filters.

### Boolean Operators

| Symbol | Meaning | Priority |
|--------|---------|----------|
| `!` | NOT (negation) | Highest |
| `&` | AND | Medium |
| `\|` | OR | Lowest |
| `( )` | Grouping | Overrides priority |

### Progressive Examples

**Single filter:**
```json
{
  "filters": {
    "f1": { "ref": "STATUS", "op": "EQ", "value": "active" }
  },
  "combineWith": "f1"
}
```

**Two filters with AND:**
```json
{
  "filters": {
    "isActive": { "ref": "STATUS", "op": "EQ", "value": "active" },
    "isAdult": { "ref": "AGE", "op": "GTE", "value": 18 }
  },
  "combineWith": "isActive & isAdult"
}
```
*Result: active AND adult users*

**With OR:**
```json
{
  "filters": {
    "isActive": { "ref": "STATUS", "op": "EQ", "value": "active" },
    "isPending": { "ref": "STATUS", "op": "EQ", "value": "pending" }
  },
  "combineWith": "isActive | isPending"
}
```
*Result: active OR pending users*

**With negation:**
```json
{
  "filters": {
    "isDeleted": { "ref": "STATUS", "op": "EQ", "value": "deleted" }
  },
  "combineWith": "!isDeleted"
}
```
*Result: NOT deleted users*

**Complex expression:**
```json
{
  "filters": {
    "hasEmail": { "ref": "EMAIL", "op": "NOT_NULL", "value": null },
    "isAdult": { "ref": "AGE", "op": "GTE", "value": 18 },
    "isVip": { "ref": "STATUS", "op": "EQ", "value": "vip" },
    "isAdmin": { "ref": "ROLE", "op": "EQ", "value": "admin" }
  },
  "combineWith": "hasEmail & isAdult & (isVip | isAdmin)"
}
```
*Result: has email AND is adult AND (is VIP OR admin)*

### Practical Shortcuts

To combine **all** filters:
- `"AND"`: Combine all with AND
- `"OR"`: Combine all with OR

```json
{
  "filters": {
    "f1": { "ref": "STATUS", "op": "EQ", "value": "active" },
    "f2": { "ref": "AGE", "op": "GTE", "value": 18 },
    "f3": { "ref": "EMAIL", "op": "NOT_NULL", "value": null }
  },
  "combineWith": "AND"
}
```
*Equivalent to: `"f1 & f2 & f3"`*

---

## 3. Projection

Choose exactly which fields to return. Less data = faster response.

### Basic Syntax

```json
{
  "filters": { ... },
  "combineWith": "...",
  "projection": ["name", "email", "age"]
}
```

**Response:**
```json
{
  "content": [
    { "name": "John", "email": "john@example.com", "age": 30 },
    { "name": "Jane", "email": "jane@example.com", "age": 25 }
  ]
}
```

### Nested Fields

To access properties of related objects:

```json
{
  "projection": ["name", "address.city", "address.country"]
}
```

**Compact syntax (equivalent):**
```json
{
  "projection": ["name", "address.city,country"]
}
```

### Without Projection

If you don't send a `projection`, all entity fields are returned.

---

## 4. Pagination

Control the amount of results and navigate through pages.

### Syntax

```json
{
  "filters": { ... },
  "combineWith": "...",
  "pagination": {
    "page": 0,
    "size": 20,
    "sort": [
      { "property": "name", "direction": "ASC" },
      { "property": "createdAt", "direction": "DESC" }
    ]
  }
}
```

| Field | Description | Default |
|-------|-------------|---------|
| `page` | Page number (starts at 0) | 0 |
| `size` | Number of elements per page | 10 |
| `sort` | Sorting criteria | - |

### Paginated Response

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 156,
  "totalPages": 8
}
```

---

## 5. Configuring Filterable Properties

FilterQL offers two approaches to declare filterable properties.

### Spring Approach (Recommended)

With Spring Boot, use annotations directly on your DTO:

```java
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

    @ExposedAs(value = "CREATED_AT", operators = {Op.GT, Op.GTE, Op.LT, Op.LTE, Op.RANGE})
    private LocalDateTime createdAt;

    // Getters...
}
```

**What this does:**
- `@Projection(from = User.class)`: Links this DTO to the `User` JPA entity
- `@Exposure(value = "users", basePath = "/api")`: Generates `POST /api/users/search`
- `@ExposedAs(value = "NAME", operators = {...})`: Declares this field as filterable

**What is auto-generated at compile time:**
- The `UserDTO_ implements PropertyReference` enum (DTO name + underscore `_`)
- The REST controller with the `/api/users/search` endpoint
- All validation logic, DSL parsing, and JPA mapping

:::info Automatic Generation
You **never** need to manually create the `PropertyReference` enum with Spring. The annotation processor generates it for you from the `@ExposedAs` annotations.
:::

### Core Approach (Programmatic)

Without Spring, or for total control, implement `PropertyReference` manually:

```java
public enum UserProperty implements PropertyReference {
    NAME, EMAIL, AGE, STATUS, CREATED_AT;

    @Override
    public Class<?> getType() {
        return switch (this) {
            case NAME, EMAIL, STATUS -> String.class;
            case AGE -> Integer.class;
            case CREATED_AT -> LocalDateTime.class;
        };
    }

    @Override
    public Set<Op> getSupportedOperators() {
        return switch (this) {
            case NAME, EMAIL -> Set.of(Op.EQ, Op.NE, Op.MATCHES, Op.IN);
            case AGE -> Set.of(Op.EQ, Op.GT, Op.GTE, Op.LT, Op.LTE, Op.RANGE);
            case STATUS -> Set.of(Op.EQ, Op.NE, Op.IN);
            case CREATED_AT -> Set.of(Op.GT, Op.GTE, Op.LT, Op.LTE, Op.RANGE);
        };
    }

    @Override
    public Class<?> getEntityType() {
        return User.class;
    }
}
```

This approach then requires manually creating the endpoint and using `FilterQueryFactory`.

### Naming Convention (Core Approach)

By default, FilterQL converts the enum name to a JPA property name:

| Enum | JPA Property |
|------|--------------|
| `NAME` | `name` |
| `EMAIL` | `email` |
| `CREATED_AT` | `createdAt` |
| `USER_STATUS` | `userStatus` |

The conversion follows the rule: `SCREAMING_SNAKE_CASE` → `camelCase`.

---

## 6. Complete Request

Here's a request using all features:

```json
{
  "filters": {
    "nameFilter": { "ref": "NAME", "op": "MATCHES", "value": "john" },
    "ageFilter": { "ref": "AGE", "op": "RANGE", "value": [25, 45] },
    "statusFilter": { "ref": "STATUS", "op": "IN", "value": ["active", "vip"] },
    "hasEmail": { "ref": "EMAIL", "op": "NOT_NULL", "value": null }
  },
  "combineWith": "(nameFilter | ageFilter) & statusFilter & hasEmail",
  "projection": ["name", "email", "status"],
  "pagination": {
    "page": 0,
    "size": 25,
    "sort": [{ "property": "name", "direction": "ASC" }]
  }
}
```

**What this does:**
1. Searches for users where (name contains "john" OR age is between 25 and 45)
2. AND who have status "active" or "vip"
3. AND who have a non-null email
4. Returns only name, email, status
5. Sorts by name, 25 results per page, first page

---

## What's Next?

You now know 80% of FilterQL. For advanced cases:

| Need | Guide |
|------|-------|
| Filter on relations (e.g., `user.address.city`) | [→ Advanced Guide](./advanced-guide#relations) |
| Create custom operators | [→ Advanced Guide](./advanced-guide#custom-operators) |
| Advanced JPA mapping | [→ Advanced Guide](./advanced-guide#jpa-mapping) |
| Collections with inline pagination | [→ Advanced Guide](./advanced-guide#collections) |
