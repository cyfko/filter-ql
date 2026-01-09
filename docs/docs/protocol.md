---
sidebar_position: 5
---

# FilterQL Protocol Specification

Formal specification of the FilterQL protocol defining message format, DSL grammar, projection syntax, validation rules, and interoperability requirements.

---

## Introduction

FilterQL is a protocol for expressing, validating, and executing dynamic filters on business entities. It guarantees interoperability, type safety, and extensibility of filtering in distributed or modular systems.

---

## Terminology

| Term | Definition |
|------|------------|
| **Filter** | Logical expression defining selection criteria on entities |
| **DSL** | Formal language for describing filters (e.g., JSON, structured string) |
| **Condition** | Logical representation of a filter, combinable (and, or, not) |
| **Property Reference** | Unique identifier of a filterable attribute of an entity |
| **Adapter** | Component responsible for translating the filter to a target execution engine |

---

## Message Format

### Canonical Structure

```json
{
  "filters": { ... },      // Dictionary of named atomic filters
  "combineWith": "...",    // Boolean DSL expression on filter keys
  "projection": [ ... ],   // (optional) List of fields to return
  "pagination": { ... }    // (optional) Page, size, sort
}
```

### Conceptual Example

**Semantic intent:** *"Find users named 'john' who are over 25 years old, returning their username, email, and their 10 most recent books (with title and year), showing 20 users per page starting from page 1."*

```json
{
  "filters": {
    "f1": { "ref": "USERNAME", "op": "EQ", "value": "john" },
    "f2": { "ref": "AGE", "op": "GT", "value": 25 }
  },
  "combineWith": "f1 & f2",
  "projection": [
    "username",
    "email",
    "books[size=10,sort=year:desc].title,year"
  ],
  "pagination": { "page": 1, "size": 20 }
}
```

**This example demonstrates:**
- Boolean filter combination (`f1 & f2`)
- Collection projection with inline pagination (`size=10`)
- Collection sorting (`sort=year:desc`)
- Compact multi-field syntax (`.title,year`)
- Top-level pagination (page 1, 20 elements per page)

### Component Details

| Component | Format |
|-----------|--------|
| **FilterDefinition** | `{ "ref": <PROPERTY_REF>, "op": <OP>, "value": <VAL> }` |
| **combineWith** | Boolean DSL expression on filter keys |
| **projection** | List of fields with optional inline pagination/sorting |
| **pagination** | `{ "page": <int>, "size": <int>, "sort": [...] }` |

---

## Boolean DSL Grammar (combineWith)

The `combineWith` field contains a boolean expression that combines atomic filters.

### Formal Grammar (EBNF)

```ebnf
(* Boolean DSL Expression *)
expression           = term , { "|" , term } ;
term                 = factor , { "&" , factor } ;
factor               = [ "!" ] , ( identifier | "(" , expression , ")" ) ;

(* Filter identifier *)
identifier           = ( letter | "_" ) , { letter | digit | "_" } ;

(* Terminals *)
letter               = "a" .. "z" | "A" .. "Z" ;
digit                = "0" .. "9" ;
```

### Operator Precedence (High to Low)

| Priority | Operator | Associativity | Type |
|----------|----------|---------------|------|
| 3 | `!` (NOT) | Right | Unary prefix |
| 2 | `&` (AND) | Left | Binary infix |
| 1 | `\|` (OR) | Left | Binary infix |
| - | `( )` (Parentheses) | - | Grouping |

### Grammar Rules

1. **Identifiers**: Must start with letter or underscore, followed by alphanumerics or underscore
2. **Whitespace**: Optional around operators and identifiers
3. **Associativity**: 
   - Left: `a & b & c` → `((a & b) & c)`
   - Right: `!a & !b` → `((!a) & (!b))`
4. **Parentheses**: Must be balanced and properly nested
5. **Valid identifiers**: Must reference keys defined in `filters`

### DSL Expression Examples

```
// Simple expressions
"f1"                    // Single filter
"f1 & f2"               // AND combination
"f1 | f2"               // OR combination
"!f1"                   // NOT negation

// Precedence demonstration
"f1 & f2 | f3"          // ((f1 & f2) | f3) - AND binds tighter than OR
"f1 | f2 & f3"          // (f1 | (f2 & f3)) - AND evaluated first
"!f1 & f2"              // ((!f1) & f2) - NOT evaluated first

// Explicit grouping with parentheses
"(f1 | f2) & f3"        // Overrides precedence
"!(f1 & f2)"            // Negates entire sub-expression

// Complex expressions
"(f1 & f2) | (f3 & !f4)"               // Multiple groups
"((a & b) | (c & d)) & !(e | f)"       // Nested logic
"!deleted & (active | pending)"        // Common filtering pattern
```

### Shorthand Syntax

Implementations MAY support these expansions:

| Shorthand | Expansion |
|-----------|-----------|
| `"AND"` | Combines all filters with AND |
| `"OR"` | Combines all filters with OR |
| `"NOT"` | Combines with AND then negates: `!(f1 & f2 & ... & fn)` |

---

## Projection Field Syntax

Projection fields support advanced syntax for collection pagination and sorting.

### Formal Grammar (EBNF)

```ebnf
(* Projection field specification *)
projection-field     = simple-field | nested-field | collection-field ;

(* Simple field without hierarchy *)
simple-field         = field-name ;

(* Nested field with dot notation *)
nested-field         = field-path , "." , field-list ;

(* Collection field with optional pagination/sorting *)
collection-field     = field-path-with-options , "." , field-list ;

(* Field path *)
field-path           = field-segment , { "." , field-segment } ;
field-segment-with-options = field-name , [ collection-options ] ;

(* Multi-field list *)
field-list           = field-name , { "," , field-name } ;

(* Collection options *)
collection-options   = "[" , option-list , "]" ;
option-list          = option , { "," , option } ;
option               = size-option | page-option | sort-option ;

(* Individual options *)
size-option          = "size=" , positive-integer ;
page-option          = "page=" , non-negative-integer ;
sort-option          = "sort=" , sort-spec , { "," , sort-spec } ;
sort-spec            = field-name , ":" , sort-direction ;
sort-direction       = "asc" | "desc" | "ASC" | "DESC" ;

(* Terminals *)
field-name           = ( letter | "_" ) , { letter | digit | "_" | "-" } ;
positive-integer     = digit , { digit } ;  (* 1..10000 enforced *)
non-negative-integer = "0" | positive-integer ;
```

### Available Options

| Option | Description | Default | Range |
|--------|-------------|---------|-------|
| `size=N` | Page size | 10 | 1 to 10000 |
| `page=P` | Page number (0-indexed) | 0 | 0+ |
| `sort=field:dir` | Sort by field | - | asc/desc |

### Syntax Examples

```json
// Simple projection
{"projection": ["id", "name", "email"]}

// Nested field
{"projection": ["id", "address.city", "address.country"]}

// Multi-fields with shared prefix
{"projection": ["id", "address.city,country,postalCode"]}
// Equivalent to: ["id", "address.city", "address.country", "address.postalCode"]

// Collection with pagination
{"projection": [
  "id",
  "name",
  "books[size=10].title,year"
]}

// Collection with sorting
{"projection": [
  "id",
  "name",
  "books[sort=year:desc].title,year"
]}

// Collection with pagination AND sorting
{"projection": [
  "id",
  "name",
  "books[size=20,page=0,sort=year:desc].title,author,year"
]}

// Multi-column sorting
{"projection": [
  "id",
  "books[sort=year:desc,title:asc].title,year"
]}

// Hierarchical pagination
{"projection": [
  "id",
  "name",
  "authors[size=10].name,books[size=5,sort=year:desc].title,year"
]}
```

---

## Validation Rules

### DSL Expressions

1. **Undefined references**: The DSL MUST NOT reference keys not present in `filters`
2. **Empty expression**: Empty or whitespace-only expressions are INVALID
3. **Unbalanced parentheses**: Expressions with unmatched `(` and `)` are INVALID
4. **Invalid operators**: Only `&`, `|`, `!`, `(`, `)` are allowed
5. **Operator placement**: Binary operators require left and right operands

### Collection Projections

Multiple references to the same collection MUST use identical options:

```json
// ❌ INVALID: conflicting options for 'books'
{
  "projection": [
    "books[size=10].title",
    "books[size=20].author"
  ]
}

// ✅ VALID: identical options
{
  "projection": [
    "books[size=10,sort=year:desc].title",
    "books[size=10,sort=year:desc].author"
  ]
}

// ✅ VALID: use multi-field syntax
{
  "projection": [
    "books[size=10,sort=year:desc].title,author"
  ]
}
```

---

## Processing Rules

1. Parse the filter message according to the DSL grammar
2. Validate each property and operator according to the contract schema
3. Build the logical condition tree
4. Pass the validated structure to an execution adapter
5. The adapter translates the structure into a native query for the target engine

---

## Interoperability Requirements

- Any implementation MUST accept and produce filters according to the defined format
- Properties and operators MUST be explicitly documented and validated
- Extensions (new operators, types, adapters) MUST respect the layered architecture

---

## Security and Robustness

- User inputs MUST be validated to prevent injection or query corruption
- Property references MUST come from a trusted schema
- The protocol DOES NOT ASSUME any specific implementation language or technology

---

## Strategic Vision

The protocol is designed to be extensible (new operators, formats, adapters) and to guarantee filter portability across heterogeneous systems.

---

## Protocol Summary

| Aspect | Value |
|--------|-------|
| **Name** | FilterQL Protocol |
| **Purpose** | Standardize the expression, validation, and execution of dynamic filters |
| **Format** | JSON |
| **Grammars** | EBNF for boolean DSL and projection syntax |
| **License** | MIT |
