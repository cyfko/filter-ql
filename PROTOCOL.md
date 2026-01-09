
# RFC: FilterQL Protocol Specification

## 1. Introduction
FilterQL is a protocol for the expression, validation, and execution of dynamic filters on business entities. It aims to guarantee interoperability, type safety, and extensibility of filtering in distributed or modular systems.

## 2. Terminology
- **Filter**: A logical expression defining selection criteria on entities.
- **DSL**: A formal language for describing filters (e.g., JSON, structured string).
- **Condition**: Logical representation of a filter, combinable (and, or, not).
- **Property Reference**: Unique identifier of a filterable attribute of an entity.
- **Adapter**: Component responsible for translating the filter to a target execution engine.

## 3. Scope and Objectives
The FilterQL protocol applies to any system requiring:
- Dynamic expression of filtering criteria on structured entities.
- Strict validation of allowed properties and operators.
- Independence from underlying execution engines or frameworks.

## 4. Conceptual Architecture
The protocol defines the following invariants:
1. A filter is expressed in a formal, serializable DSL (e.g., JSON).
2. The filter is transformed into a logical structure (condition tree).
3. Each property and operator is validated according to an explicit contract.
4. The validated filter is passed to an adapter for execution.

## 5. Message Format (Abstract Example)

### 5.1. Canonical Filter Message Structure
The FilterQL protocol is based on the following structure:

- **filters**: Dictionary of named atomic filters (key → FilterDefinition)
- **combineWith**: Boolean (DSL) expression on filter keys (e.g., "f1 & f2 | f3")
- **projection**: (optional) List of fields to return
- **pagination**: (optional) Page, size, sorting

#### Simple Conceptual Example (pseudo-JSON)

**Semantic Intent:** *"Find users named 'john' who are older than 25, returning their username, email, and their 10 most recent books (with title and year), showing 20 users per page starting from page 1."*

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
- Multi-field compact syntax (`.title,year`)
- Top-level pagination (page 1, 20 items per page)

#### Component Details
- **FilterDefinition**: { "ref": <PROPERTY_REF>, "op": <OP>, "value": <VAL> }
- **combineWith**: Boolean DSL expression on filter keys (see Boolean DSL Grammar)
- **projection**: List of fields with optional inline collection pagination/sorting (see Projection Field Syntax)
- **pagination**: Object { "page": <int>, "size": <int>, "sort": [ { "field": <str>, "direction": "ASC|DESC" } ] } (optional)

### 5.2. Boolean DSL Grammar (combineWith)

The `combineWith` field contains a boolean expression that combines atomic filters using logical operators.

#### Formal Grammar (Extended BNF)

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

#### Operator Precedence (High to Low)

1. **`!` (NOT)** - Precedence 3, right-associative, unary prefix
2. **`&` (AND)** - Precedence 2, left-associative, binary infix
3. **`|` (OR)** - Precedence 1, left-associative, binary infix
4. **`( )` (Parentheses)** - Override precedence, force grouping

#### Grammar Rules

1. **Identifiers**: Must start with letter or underscore, followed by alphanumeric or underscore
2. **Whitespace**: Optional whitespace allowed around operators and identifiers
3. **Operator Associativity**: 
   - Left-associative: `a & b & c` → `((a & b) & c)`
   - Right-associative: `!a & !b` → `((!a) & (!b))`
4. **Parentheses**: Must be balanced and properly nested
5. **Valid Identifiers**: Must reference filter keys defined in `filters` dictionary

#### DSL Expression Examples

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
"(f1 | f2) & f3"        // Override precedence
"!(f1 & f2)"            // Negate entire subexpression
"((f1 & f2) | f3)"      // Extra parentheses (valid)

// Complex expressions
"(f1 & f2) | (f3 & !f4)"               // Multiple groups
"((a & b) | (c & d)) & !(e | f)"       // Nested logic
"!deleted & (active | pending)"        // Common filtering pattern
```

#### Shorthand Syntax (Optional)

Implementations MAY support these shorthand expansions:

```
"AND"  → Combine all filters with AND operator
"OR"   → Combine all filters with OR operator  
"NOT"  → Combine all filters with AND, then negate: !(f1 & f2 & ... & fn)
```

#### Validation Rules

1. **Undefined References**: DSL MUST NOT reference filter keys not present in `filters`
2. **Empty Expression**: Empty or whitespace-only expressions are INVALID
3. **Unbalanced Parentheses**: Expressions with mismatched `(` and `)` are INVALID
4. **Invalid Operators**: Only `&`, `|`, `!`, `(`, `)` are allowed
5. **Operator Placement**: Binary operators require left and right operands
6. **Expression Length**: Implementations SHOULD enforce maximum length limits (e.g., 1000 characters)

#### Error Examples

```json
// ❌ INVALID: Undefined filter reference
{"filters": {"f1": {...}}, "combineWith": "f1 & f2"}  // f2 not defined

// ❌ INVALID: Unbalanced parentheses
{"filters": {"f1": {...}}, "combineWith": "(f1 & f2"}

// ❌ INVALID: Invalid operator
{"filters": {"f1": {...}}, "combineWith": "f1 && f2"}  // Use & not &&

// ❌ INVALID: Binary operator without operands
{"filters": {"f1": {...}}, "combineWith": "& f1"}

// ❌ INVALID: Empty expression
{"filters": {"f1": {...}}, "combineWith": ""}
```

### 5.3. Projection Field Syntax

Projection fields support advanced syntax for collection pagination and sorting using inline bracketed options.

#### Formal Grammar (Extended BNF)

```ebnf
(* Projection Field Specification *)
projection-field     = simple-field | nested-field | collection-field ;

(* Simple field without hierarchy *)
simple-field         = field-name ;

(* Nested field with dot notation *)
nested-field         = field-path , "." , field-list ;

(* Collection field with optional pagination/sorting *)
collection-field     = field-path-with-options , "." , field-list ;

(* Field path (may include collection options) *)
field-path           = field-segment , { "." , field-segment } ;
field-path-with-options = field-segment-with-options , { "." , field-segment-with-options } ;

(* Field segment (name or name with options) *)
field-segment        = field-name ;
field-segment-with-options = field-name , [ collection-options ] ;

(* Multi-field list (comma-separated after last dot) *)
field-list           = field-name , { "," , field-name } ;

(* Collection pagination/sorting options *)
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
positive-integer     = digit , { digit } ;  (* 1..10000 enforced at runtime *)
non-negative-integer = "0" | positive-integer ;
letter               = "a" .. "z" | "A" .. "Z" ;
digit                = "0" .. "9" ;
```

#### Grammar Notes

1. **Whitespace**: Whitespace is allowed around commas and within brackets, but stripped during parsing
2. **Field Names**: Must start with letter or underscore, contain only alphanumeric, underscore, hyphen
3. **Sort Direction**: Case-insensitive (`asc`/`ASC`/`Asc` are equivalent)
4. **Size Limits**: `size` must be 1-10000 (validated at parse time)
5. **Page Index**: `page` is 0-indexed (0 = first page)
6. **Option Order**: Options within brackets can appear in any order
7. **Multi-Field**: Comma-separated fields must appear after the last dot in the path

#### Basic Syntax Examples
```
fieldName                                    // Simple field
parent.child.field                           // Nested field
collection.field1,field2,field3              // Multi-field with shared prefix
collection[options].field                    // Collection with pagination/sorting
parent[options].collection[options].field    // Hierarchical pagination
```

#### Collection Options

Options are specified in brackets immediately after the collection name:

- **size=N** - Limit collection to N items (1 to 10000, default: 10)
- **page=P** - Fetch page P (0-indexed, default: 0)
- **sort=field:dir** - Sort by field with direction (asc or desc)
  - Multiple sorts: `sort=field1:asc,field2:desc`

#### Syntax Examples

```json
// Simple projection
{"projection": ["id", "name", "email"]}

// Nested field projection
{"projection": ["id", "address.city", "address.country"]}

// Multi-field with shared prefix (compact syntax)
{"projection": ["id", "address.city,country,postalCode"]}
// Equivalent to: ["id", "address.city", "address.country", "address.postalCode"]

// Collection with pagination
{"projection": [
  "id",
  "name",
  "books[size=10].title,year"  // First 10 books only
]}

// Collection with sorting
{"projection": [
  "id",
  "name",
  "books[sort=year:desc].title,year"  // All books, sorted by year DESC
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
  "books[sort=year:desc,title:asc].title,year"  // Sort by year DESC, then title ASC
]}

// Hierarchical pagination (multiple levels)
{"projection": [
  "id",
  "name",
  "authors[size=10].name,books[size=5,sort=year:desc].title,year"
  // ↑ 10 authors per entity
  //                      ↑ 5 books per author, sorted by year
]}

// Page navigation
{"projection": ["books[size=10,page=0].title"]}  // First page (items 0-9)
{"projection": ["books[size=10,page=1].title"]}  // Second page (items 10-19)
{"projection": ["books[size=10,page=2].title"]}  // Third page (items 20-29)
```

#### Syntax Rules

See [Formal Grammar (EBNF)](#formal-grammar-extended-bnf) for precise specification.

1. **Field Names**: Alphanumeric characters, underscores, and hyphens only (`[a-zA-Z0-9_-]+`)
2. **Brackets**: Collection options must be enclosed in square brackets `[...]`
3. **Multi-Fields**: Use comma separator after last dot (e.g., `prefix.field1,field2`)
4. **Sort Direction**: Must be `asc` or `desc` (case-insensitive)
5. **Page Size Limits**: 1 to 10000 (enforced at parse time)
6. **Option Consistency**: Multiple references to the same collection must use identical options

#### Validation

```json
// ❌ Invalid: conflicting options for same collection
{
  "projection": [
    "books[size=10].title",
    "books[size=20].author"  // ERROR: books has conflicting size options
  ]
}

// ✅ Valid: consistent options
{
  "projection": [
    "books[size=10,sort=year:desc].title",
    "books[size=10,sort=year:desc].author"  // OK: identical options
  ]
}

// ✅ Valid: use multi-field syntax instead
{
  "projection": [
    "books[size=10,sort=year:desc].title,author"  // OK: single declaration
  ]
}
```

#### Performance Considerations

- **Default Fetch**: Collections without options are fetched entirely (no limit)
- **Batch Fetching**: Implementations SHOULD use batch fetching to avoid N+1 queries
- **Memory Impact**: Pagination reduces memory footprint for large collections
- **Index Usage**: Sort fields SHOULD be indexed for optimal performance

#### Important Notes
- There is NO nested composition of conditions in the protocol format.
- All complex logic is expressed via the boolean expression on atomic filter keys.
- Collection pagination/sorting is INDEPENDENT of top-level pagination (applies per parent entity).

#### Complex Conceptual Example (pseudo-JSON)
```json
{
  "filters": {
    "f1": { "ref": "USERNAME", "op": "EQ", "value": "john" },
    "f2": { "ref": "AGE", "op": "GT", "value": 25 },
    "f3": { "ref": "STATUS", "op": "IN", "value": ["ACTIVE", "PENDING"] },
    "f4": { "ref": "EMAIL", "op": "MATCHES", "value": "%@example.com" },
    "f5": { "ref": "CREATED_AT", "op": "RANGE", "value": ["2024-01-01", "2025-01-01"] }
  },
  "combineWith": "(f1 | f4) & f2 & (f3 | !f5)",
  "projection": ["username", "email", "status", "createdAt"],
  "pagination": { "page": 2, "size": 50, "sort": [ { "field": "createdAt", "direction": "DESC" } ] }
}
```
This format allows the expression of complex boolean queries, inclusions/exclusions, date ranges, partial matches, etc., while remaining strictly flat and readable.

## 6. Processing Rules
1. Parse the filter message according to the DSL grammar.
2. Validate each property and operator according to the contract schema.
3. Build the logical condition tree.
4. Pass the validated structure to an execution adapter.
5. The adapter translates the structure into a native query for the target engine.

## 7. Interoperability Requirements
- Any implementation MUST accept and produce filters according to the defined format.
- Properties and operators MUST be explicitly documented and validated.
- Extensions (new operators, types, adapters) MUST respect the layered architecture.

## 8. Security and Robustness
- User input MUST be validated to prevent injection or query corruption.
- Property references MUST originate from a trusted schema.
- The protocol does NOT assume any specific implementation language or technology.

## 9. Abstract Use Cases
- Filtering a collection of entities according to dynamic criteria.
- Logical composition of filters (and, or, not).
- Contractual validation of properties and operators.
- Adapting the filter to different execution engines (SQL, NoSQL, API, etc.).

## 10. Strategic Vision
The protocol is designed to be extensible (new operators, formats, adapters) and to guarantee filter portability across heterogeneous systems.

---

## 11. Protocol Summary
- **Name**: FilterQL Protocol
- **Purpose**: Standardize the expression, validation, and execution of dynamic filters on structured entities.
- **Key Actors**: Producers and consumers of dynamic filters, execution engines, integration adapters.
- **Format**: Serialized DSL (JSON or equivalent) describing contractual conditions and operators.
- **Interoperability**: Language-independent, extensible, secure.
- **Maturity**: Stable, evolving, production-grade.
