---
sidebar_position: 1
---

# PropertyReference

L'interface `PropertyReference` est le contrat fondamental de FilterQL pour définir des propriétés filtrables de manière type-safe.

---

## Package

```java
io.github.cyfko.filterql.core.validation.PropertyReference
```

---

## Interface

```java
public interface PropertyReference {
    
    /**
     * Retourne le type Java de la propriété représentée.
     * @return la classe Java représentant le type de la propriété
     */
    Class<?> getType();

    /**
     * Retourne la collection immuable des opérateurs par défaut supportés.
     * @return un Set immuable d'opérateurs supportés
     */
    Set<Op> getSupportedOperators();

    /**
     * Retourne le type Java de l'entité JPA cible.
     */
    Class<?> getEntityType();
}
```

---

## Pourquoi des Enums ?

FilterQL utilise des enums pour les références de propriété pour plusieurs raisons :

| Avantage | Description |
|----------|-------------|
| **Sécurité à la compilation** | Pas de fautes de frappe dans les noms de propriété |
| **Autocomplétion IDE** | Suggestion automatique des propriétés disponibles |
| **Compatibilité explicite des opérateurs** | Chaque propriété déclare ses opérateurs valides |
| **Métadonnées centralisées** | Type, opérateurs et entité définis au même endroit |

---

## Pattern d'Implémentation

### Structure Recommandée

Utilisez des expressions `switch` (Java 14+) pour une syntaxe concise :

```java
import io.github.cyfko.filterql.core.validation.PropertyReference;
import io.github.cyfko.filterql.core.validation.Op;
import java.util.Set;

public enum UserPropertyRef implements PropertyReference {
    USERNAME,
    EMAIL,
    AGE,
    STATUS,
    CITY,
    CREATED_DATE;

    @Override
    public Class<?> getType() {
        return switch (this) {
            case USERNAME, EMAIL, CITY -> String.class;
            case AGE -> Integer.class;
            case STATUS -> UserStatus.class;
            case CREATED_DATE -> LocalDateTime.class;
        };
    }

    @Override
    public Set<Op> getSupportedOperators() {
        return switch (this) {
            case USERNAME, EMAIL -> Set.of(Op.EQ, Op.MATCHES, Op.IN);
            case AGE -> Set.of(Op.EQ, Op.GT, Op.GTE, Op.LT, Op.LTE, Op.RANGE);
            case STATUS -> Set.of(Op.EQ, Op.NE, Op.IN, Op.NOT_IN);
            case CITY -> Set.of(Op.EQ, Op.MATCHES, Op.IS_NULL, Op.NOT_NULL);
            case CREATED_DATE -> Set.of(Op.GT, Op.GTE, Op.LT, Op.LTE, Op.RANGE);
        };
    }

    @Override
    public Class<?> getEntityType() {
        return User.class;
    }
}
```

:::warning Anti-Pattern
**N'utilisez PAS** de constructeurs avec des champs privés. Le pattern `switch` est plus clair et évite la gestion d'état inutile.

```java
// ❌ À ÉVITER
public enum UserPropertyRef implements PropertyReference {
    USERNAME(String.class, Set.of(Op.EQ, Op.MATCHES));
    
    private final Class<?> type;
    private final Set<Op> ops;
    
    // Constructeur et getters...
}
```
:::

---

## Propriétés Imbriquées

Pour les propriétés imbriquées (relations JPA), le mapping se fait dans `JpaFilterContext`, pas dans l'enum :

```java
public enum OrderPropertyRef implements PropertyReference {
    ORDER_NUMBER,
    CUSTOMER_NAME,    // Propriété imbriquée: customer.name
    CUSTOMER_EMAIL,   // Propriété imbriquée: customer.email
    TOTAL_AMOUNT;

    @Override
    public Class<?> getType() {
        return switch (this) {
            case ORDER_NUMBER, CUSTOMER_NAME, CUSTOMER_EMAIL -> String.class;
            case TOTAL_AMOUNT -> BigDecimal.class;
        };
    }
    
    // ... autres méthodes
}

// Le mapping imbriqué est défini dans JpaFilterContext
JpaFilterContext<OrderPropertyRef> context = new JpaFilterContext<>(
    OrderPropertyRef.class,
    ref -> switch (ref) {
        case ORDER_NUMBER -> "orderNumber";
        case CUSTOMER_NAME -> "customer.name";      // Navigation JPA
        case CUSTOMER_EMAIL -> "customer.email";    // Navigation JPA
        case TOTAL_AMOUNT -> "totalAmount";
    }
);
```

---

## Utilitaires d'Opérateurs

Le module core fournit des constantes pour les ensembles d'opérateurs communs :

```java
import io.github.cyfko.filterql.core.utils.OperatorUtils;

public enum ProductPropertyRef implements PropertyReference {
    NAME,
    PRICE,
    STOCK,
    CATEGORY;

    @Override
    public Set<Op> getSupportedOperators() {
        return switch (this) {
            case NAME, CATEGORY -> OperatorUtils.FOR_TEXT;     // EQ, MATCHES, IN, NOT_IN
            case PRICE -> OperatorUtils.FOR_NUMBER;            // EQ, GT, GTE, LT, LTE, RANGE
            case STOCK -> OperatorUtils.FOR_NUMBER;
        };
    }
}
```

### Constantes Disponibles

| Constante | Opérateurs |
|-----------|------------|
| `FOR_TEXT` | `EQ`, `NE`, `MATCHES`, `NOT_MATCHES`, `IN`, `NOT_IN`, `IS_NULL`, `NOT_NULL` |
| `FOR_NUMBER` | `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `RANGE`, `NOT_RANGE` |
| `FOR_DATE` | `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `RANGE`, `NOT_RANGE` |
| `FOR_ENUM` | `EQ`, `NE`, `IN`, `NOT_IN` |
| `FOR_BOOLEAN` | `EQ`, `NE` |

---

## Validation

FilterQL valide automatiquement la compatibilité opérateur/propriété lors de la création des filtres :

```java
// ✅ Valide : MATCHES est supporté pour USERNAME
var nameFilter = new FilterDefinition<>(UserPropertyRef.USERNAME, Op.MATCHES, "%john%");

// ❌ Exception : RANGE n'est pas supporté pour USERNAME
var invalidFilter = new FilterDefinition<>(UserPropertyRef.USERNAME, Op.RANGE, List.of("a", "z"));
// Throws: FilterValidationException
```

---

## Prochaines Étapes

- [FilterDefinition](filter-definition) - Créer des définitions de filtre atomiques
- [Syntaxe DSL](dsl-syntax) - Composer des filtres avec le DSL booléen
