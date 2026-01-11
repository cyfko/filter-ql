---
sidebar_position: 2
---

# FilterDefinition

La classe `FilterDefinition` représente un filtre atomique immutable composé d'une référence de propriété, d'un opérateur et d'une valeur.

---

## Package

```java
io.github.cyfko.filterql.core.model.FilterDefinition
```

---

## Structure

```java
public record FilterDefinition<P extends Enum<P> & PropertyReference>(
    P ref,           // Référence de propriété (enum)
    String op,       // Code d'opérateur (insensible à la casse)
    Object value     // Valeur du filtre (nullable pour opérateurs unaires)
) { }
```

---

## Composants

### Référence de Propriété (`ref`)

Valeur enum implémentant `PropertyReference` qui identifie la propriété à filtrer.

```java
var filter = new FilterDefinition<>(UserPropertyRef.EMAIL, Op.EQ, "john@example.com");
//                                   ↑ ref
```

### Code d'Opérateur (`op`)

Identifiant de l'opération de filtrage. Peut être :

- **Opérateur standard** : Un des 14 opérateurs définis dans `Op`
- **Opérateur personnalisé** : Code personnalisé géré via `CustomOperatorResolver` ou `PredicateResolverMapping` dans le JPA adapter

```java
// Utilisation de l'enum Op (recommandé)
var filter1 = new FilterDefinition<>(ref, Op.MATCHES, "%john%");

// Utilisation d'une chaîne (insensible à la casse)
var filter2 = new FilterDefinition<>(ref, "matches", "%john%");  // Normalisé en "MATCHES"
```

### Valeur (`value`)

L'opérande du filtre. Le type dépend de l'opérateur :

| Opérateur | Type de Valeur |
|-----------|----------------|
| `EQ`, `NE`, `GT`, `LT`, `GTE`, `LTE` | Valeur simple correspondant au type de propriété |
| `MATCHES`, `NOT_MATCHES` | `String` avec wildcards (`%`, `_`) |
| `IN`, `NOT_IN` | `Collection<?>` |
| `RANGE`, `NOT_RANGE` | `List<?>` avec exactement 2 éléments |
| `IS_NULL`, `NOT_NULL` | `null` (obligatoire) |

---

## Opérateurs Standards

L'enum `Op` définit 14 opérateurs standards :

```java
package io.github.cyfko.filterql.core.validation;

public enum Op {
    // Comparaison
    EQ("=", "EQ"),           // Égalité
    NE("!=", "NE"),          // Inégalité
    GT(">", "GT"),           // Supérieur à
    GTE(">=", "GTE"),        // Supérieur ou égal
    LT("<", "LT"),           // Inférieur à
    LTE("<=", "LTE"),        // Inférieur ou égal
    
    // Pattern matching
    MATCHES("LIKE", "MATCHES"),           // Correspondance pattern (SQL LIKE)
    NOT_MATCHES("NOT LIKE", "NOT_MATCHES"),
    
    // Collections
    IN("IN", "IN"),                       // Appartenance
    NOT_IN("NOT IN", "NOT_IN"),
    
    // Null checks
    IS_NULL("IS NULL", "IS_NULL"),        // Est null
    NOT_NULL("IS NOT NULL", "NOT_NULL"),  // N'est pas null
    
    // Range
    RANGE("BETWEEN", "RANGE"),            // Plage inclusive
    NOT_RANGE("NOT BETWEEN", "NOT_RANGE"),
    
    // Extension
    CUSTOM(null, null);                   // Marqueur pour opérateurs personnalisés
}
```

---

## Exemples d'Utilisation

### Opérateurs de Comparaison

```java
// Égalité
var eqFilter = new FilterDefinition<>(UserPropertyRef.STATUS, Op.EQ, UserStatus.ACTIVE);

// Supérieur à
var gtFilter = new FilterDefinition<>(UserPropertyRef.AGE, Op.GT, 18);

// Inférieur ou égal
var lteFilter = new FilterDefinition<>(ProductPropertyRef.PRICE, Op.LTE, 99.99);
```

### Pattern Matching

```java
// Contient "john" (insensible à la casse selon FilterConfig)
var containsFilter = new FilterDefinition<>(UserPropertyRef.USERNAME, Op.MATCHES, "%john%");

// Commence par "admin"
var startsWithFilter = new FilterDefinition<>(UserPropertyRef.EMAIL, Op.MATCHES, "admin%");

// Se termine par ".com"
var endsWithFilter = new FilterDefinition<>(UserPropertyRef.EMAIL, Op.MATCHES, "%.com");

// Exclusion de pattern
var notMatchesFilter = new FilterDefinition<>(UserPropertyRef.EMAIL, Op.NOT_MATCHES, "%test%");
```

### Collections

```java
// IN : appartenance à une liste
var inFilter = new FilterDefinition<>(
    UserPropertyRef.STATUS,
    Op.IN,
    List.of(UserStatus.ACTIVE, UserStatus.PENDING, UserStatus.APPROVED)
);

// NOT IN : exclusion d'une liste
var notInFilter = new FilterDefinition<>(
    UserPropertyRef.ROLE,
    Op.NOT_IN,
    List.of("ADMIN", "MODERATOR")
);
```

### Plages (Range)

```java
// Âge entre 18 et 65 (inclus)
var ageRangeFilter = new FilterDefinition<>(
    UserPropertyRef.AGE,
    Op.RANGE,
    List.of(18, 65)  // Exactement 2 éléments requis
);

// Dates entre deux bornes
var dateRangeFilter = new FilterDefinition<>(
    OrderPropertyRef.CREATED_AT,
    Op.RANGE,
    List.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31))
);

// Exclusion de plage
var notRangeFilter = new FilterDefinition<>(
    ProductPropertyRef.PRICE,
    Op.NOT_RANGE,
    List.of(0, 10)  // Exclure les prix de 0 à 10
);
```

### Vérifications Null

```java
// Propriété est null
var isNullFilter = new FilterDefinition<>(
    UserPropertyRef.DELETED_AT,
    Op.IS_NULL,
    null  // Valeur DOIT être null
);

// Propriété n'est pas null
var notNullFilter = new FilterDefinition<>(
    UserPropertyRef.EMAIL,
    Op.NOT_NULL,
    null  // Valeur DOIT être null
);
```

---

## Validation

La validation se fait en deux phases :

### Phase 1 : Eager (au constructeur)

- `ref` ne peut pas être null
- `op` ne peut pas être null ou vide
- `op` est normalisé en majuscules
- `IS_NULL` doit avoir `value = null`
- Le mot-clé `CUSTOM` est réservé

### Phase 2 : Différée (à la résolution)

- Existence de l'opérateur personnalisé → vérifié dans `FilterContext.toCondition()`
- Compatibilité type opérateur-valeur → vérifié à la résolution du filtre
- Compatibilité propriété-opérateur → vérifié via `PropertyReference`

```java
// ✅ Valide : Phase 1 passe
var filter = new FilterDefinition<>(UserPropertyRef.AGE, Op.EQ, 25);

// ❌ Exception Phase 1 : ref null
var invalid1 = new FilterDefinition<>(null, Op.EQ, "value");
// Throws: NullPointerException

// ❌ Exception Phase 1 : op vide
var invalid2 = new FilterDefinition<>(UserPropertyRef.AGE, "", 25);
// Throws: FilterDefinitionException

// ❌ Exception Phase 1 : IS_NULL avec valeur non-null
var invalid3 = new FilterDefinition<>(UserPropertyRef.EMAIL, Op.IS_NULL, "not null");
// Throws: FilterDefinitionException
```

---

## Immutabilité et Thread-Safety

`FilterDefinition` est un `record` Java, donc :

- **Immutable** : Les champs sont `final` et ne peuvent pas être modifiés
- **Thread-safe** : Peut être partagé entre threads sans synchronisation
- **Hashable** : Implémente correctement `equals()` et `hashCode()`

```java
// Peut être stocké dans des collections thread-safe
Map<String, FilterDefinition<UserPropertyRef>> filterCache = new ConcurrentHashMap<>();
filterCache.put("activeUsers", new FilterDefinition<>(UserPropertyRef.STATUS, Op.EQ, UserStatus.ACTIVE));
```

---

## Prochaines Étapes

- [Syntaxe DSL](dsl-syntax) - Composer plusieurs filtres avec la logique booléenne
- [Opérateurs Personnalisés](custom-operators) - Créer des opérateurs métier spécifiques
