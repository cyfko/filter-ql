---
sidebar_position: 1
---

# Référence Core

Documentation de référence complète pour le module `filterql-core` (version 4.0.0).

---

## Coordonnées Maven

```xml
<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>filterql-core</artifactId>
    <version>4.0.0</version>
</dependency>
```

---

## Structure des Packages

```
io.github.cyfko.filterql.core
├── api/                        # Abstractions core
│   ├── Condition              # Conditions composables
│   ├── DslParser              # Contrat de parsing DSL
│   ├── FilterContext          # Interface de pont backend
│   └── FilterTree             # Représentation AST
├── spi/                        # Service Provider Interfaces
│   ├── FilterQuery            # Façade de cycle de vie
│   ├── PredicateResolver      # Générateur de prédicats différés
│   ├── ExecutionStrategy      # Contrat de stratégie d'exécution
│   ├── QueryExecutor          # Coordinateur d'exécution
│   ├── CustomOperatorProvider # Extension d'opérateur personnalisé
│   └── OperatorProviderRegistry # Enregistrement d'opérateurs
├── model/                      # Structures de données immutables
│   ├── FilterRequest          # Conteneur de requête
│   ├── FilterDefinition       # Spécification de filtre atomique
│   ├── Pagination             # Métadonnées de pagination
│   ├── SortBy                 # Spécification de tri
│   └── QueryExecutionParams   # Paramètres d'exécution
├── validation/                 # Type safety & validation
│   ├── PropertyReference      # Refs de propriété basées sur enum
│   └── Op                     # Opérateurs standards
├── exception/                  # Hiérarchie d'exceptions
│   ├── DSLSyntaxException     # Erreurs de parsing DSL
│   ├── FilterValidationException # Échecs de validation
│   └── FilterDefinitionException # Erreurs de définition
└── FilterQueryFactory          # Façade principale
```

---

## API Principale

### FilterQueryFactory

Point d'entrée principal pour créer des instances `FilterQuery`.

```java
package io.github.cyfko.filterql.core;

public class FilterQueryFactory {
    
    /**
     * Crée un FilterQuery avec le parser DSL par défaut.
     * 
     * @param context Le contexte pour la résolution de conditions
     * @return nouvelle instance FilterQuery
     */
    public static <E> FilterQuery<E> of(FilterContext context);
    
    /**
     * Crée un FilterQuery avec parser et contexte personnalisés.
     * 
     * @param context Le contexte pour la résolution de conditions
     * @param dslParser Le parser pour les expressions DSL
     * @return nouvelle instance FilterQuery
     */
    public static <E> FilterQuery<E> of(FilterContext context, DslParser dslParser);
    
    /**
     * Crée un FilterQuery avec policy de projection.
     * 
     * @param context Le contexte pour la résolution de conditions
     * @param dslParser Le parser pour les expressions DSL
     * @param policy La policy de projection pour l'exécution
     * @return nouvelle instance FilterQuery
     */
    public static <E> FilterQuery<E> of(
        FilterContext context, 
        DslParser dslParser, 
        ProjectionPolicy policy
    );
}
```

#### Exemple d'Utilisation

```java
// Avec parser par défaut
FilterQuery<User> query = FilterQueryFactory.of(context);

// Avec parser personnalisé
FilterQuery<User> query = FilterQueryFactory.of(context, new CustomDslParser());

// Avec policy de projection
FilterQuery<User> query = FilterQueryFactory.of(
    context, 
    new BasicDslParser(), 
    ProjectionPolicy.defaults()
);
```

---

### FilterContext

Interface de pont entre les définitions de filtre et l'exécution backend.

```java
package io.github.cyfko.filterql.core.api;

public interface FilterContext {
    
    /**
     * Transforme une FilterDefinition en Condition.
     * 
     * @param argKey clé pour récupérer la valeur depuis le registre d'arguments
     * @param ref la référence de propriété
     * @param op l'opérateur appliqué (insensible à la casse)
     * @return la Condition créée
     * @throws FilterDefinitionException si la traduction échoue
     */
    <P extends Enum<P> & PropertyReference> Condition toCondition(
        String argKey, 
        P ref, 
        String op
    ) throws FilterDefinitionException;
    
    /**
     * Convertit un arbre de Condition en PredicateResolver exécutable.
     * 
     * @param condition l'arbre de conditions logique
     * @param params paramètres d'exécution (arguments + projection optionnelle)
     * @return un PredicateResolver pour générer des prédicats backend
     * @throws FilterDefinitionException si la transformation échoue
     */
    PredicateResolver<?> toResolver(
        Condition condition, 
        QueryExecutionParams params
    ) throws FilterDefinitionException;
}
```

---

### Condition

Représentation composable d'une condition de filtre.

```java
package io.github.cyfko.filterql.core.api;

public interface Condition {
    
    /**
     * Combine cette condition avec une autre en utilisant AND logique.
     * 
     * @param other l'autre condition
     * @return nouvelle condition composite
     */
    Condition and(Condition other);
    
    /**
     * Combine cette condition avec une autre en utilisant OR logique.
     * 
     * @param other l'autre condition
     * @return nouvelle condition composite
     */
    Condition or(Condition other);
    
    /**
     * Crée une négation de cette condition.
     * 
     * @return condition négative
     */
    Condition negate();
    
    /**
     * Retourne le resolver de prédicat associé.
     * 
     * @return le PredicateResolver sous-jacent
     */
    PredicateResolver<?> getResolver();
}
```

---

### FilterQuery (SPI)

Interface de cycle de vie de requête de filtre.

```java
package io.github.cyfko.filterql.core.spi;

public interface FilterQuery<E> {
    
    /**
     * Résout une FilterRequest en PredicateResolver.
     * 
     * @param request la requête de filtre complète
     * @return resolver de prédicat prêt à l'exécution
     */
    <P extends Enum<P> & PropertyReference> PredicateResolver<E> toResolver(
        FilterRequest<P> request
    );
    
    /**
     * Crée un QueryExecutor pour la requête.
     * 
     * @param request la requête de filtre complète
     * @return executor configuré pour la stratégie
     */
    <P extends Enum<P> & PropertyReference> QueryExecutor<E> toExecutor(
        FilterRequest<P> request
    );
}
```

---

### PredicateResolver (SPI)

Générateur de prédicats différés pour JPA Criteria API.

```java
package io.github.cyfko.filterql.core.spi;

import jakarta.persistence.criteria.*;

@FunctionalInterface
public interface PredicateResolver<E> {
    
    /**
     * Résout un prédicat JPA à partir des composants de requête.
     * 
     * @param root la racine de l'entité
     * @param query la requête criteria
     * @param cb le constructeur criteria
     * @return le prédicat JPA résolu
     */
    Predicate resolve(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder cb);
}
```

---

### QueryExecutor (SPI)

Coordinateur d'exécution de requête.

```java
package io.github.cyfko.filterql.core.spi;

import jakarta.persistence.EntityManager;

public interface QueryExecutor<R> {
    
    /**
     * Exécute avec la stratégie spécifiée.
     * 
     * @param em l'EntityManager JPA
     * @param strategy la stratégie d'exécution
     * @return résultats de la requête
     */
    R executeWith(EntityManager em, ExecutionStrategy<R> strategy);
}
```

---

### ExecutionStrategy (SPI)

Contrat de stratégie d'exécution.

```java
package io.github.cyfko.filterql.core.spi;

public interface ExecutionStrategy<R> {
    
    /**
     * Retourne la classe de projection DTO.
     */
    Class<?> getProjectionClass();
    
    /**
     * Exécute la requête et retourne les résultats.
     */
    R execute(/* paramètres dépendants de l'implémentation */);
}
```

---

## Modèles

### FilterRequest

Conteneur immutable pour une requête de filtre complète.

```java
package io.github.cyfko.filterql.core.model;

public record FilterRequest<P extends Enum<P> & PropertyReference>(
    Map<String, FilterDefinition<P>> filters,  // Définitions de filtres
    String combineWith,                         // Expression DSL booléenne
    Set<String> projection,                     // Champs DTO optionnels
    Pagination pagination                       // Configuration de pagination
) {
    
    /** Vérifie si des filtres sont présents */
    public boolean hasFilters();
    
    /** Vérifie si une projection est demandée */
    public boolean hasProjection();
    
    /** Vérifie si la pagination est configurée */
    public boolean hasPagination();
    
    /** Builder fluent */
    public static <R extends Enum<R> & PropertyReference> Builder<R> builder();
}
```

#### Builder

```java
public static class Builder<P extends Enum<P> & PropertyReference> {
    
    /** Ajoute une définition de filtre nommée */
    public Builder<P> filter(String name, FilterDefinition<P> definition);
    
    /** Ajoute un filtre avec opérateur string */
    public Builder<P> filter(String name, P ref, String op, Object value);
    
    /** Ajoute un filtre avec opérateur typé */
    public Builder<P> filter(String name, P ref, Op op, Object value);
    
    /** Ajoute plusieurs filtres */
    public Builder<P> filters(Map<String, FilterDefinition<P>> filters);
    
    /** Définit l'expression DSL */
    public Builder<P> combineWith(String expression);
    
    /** Définit la projection */
    public Builder<P> projection(Set<String> projection);
    public Builder<P> projection(String... fields);
    
    /** Configure la pagination */
    public Builder<P> pagination(int pageNumber, int pageSize);
    public Builder<P> pagination(int pageNumber, int pageSize, 
                                 String sortField, String sortDirection,
                                 String... sortFields);
    
    /** Construit la requête immutable */
    public FilterRequest<P> build();
}
```

---

### FilterDefinition

Spécification de filtre atomique immutable.

```java
package io.github.cyfko.filterql.core.model;

public record FilterDefinition<P extends Enum<P> & PropertyReference>(
    P ref,           // Référence de propriété
    String op,       // Code d'opérateur (normalisé en majuscules)
    Object value     // Valeur du filtre (nullable pour unaires)
) {
    
    // Constructeur alternatif avec enum Op
    public FilterDefinition(P ref, Op op, Object value);
}
```

---

### Pagination

Configuration de pagination.

```java
package io.github.cyfko.filterql.core.model;

public record Pagination(
    int page,          // Numéro de page (1-indexé)
    int size,          // Taille de page
    List<SortBy> sort  // Critères de tri (nullable)
) { }
```

---

### SortBy

Spécification de tri.

```java
package io.github.cyfko.filterql.core.model;

public record SortBy(
    String field,      // Nom du champ
    String direction   // "ASC" ou "DESC"
) { }
```

---

### QueryExecutionParams

Paramètres d'exécution de requête.

```java
package io.github.cyfko.filterql.core.model;

public record QueryExecutionParams(
    Map<String, Object> arguments,  // Clé d'argument → valeur
    Set<String> projection          // Chemins de champs DTO (nullable)
) {
    
    /** Crée des params sans projection */
    public static QueryExecutionParams of(Map<String, Object> arguments);
    
    /** Crée des params avec projection */
    public static QueryExecutionParams withProjection(
        Map<String, Object> arguments, 
        Set<String> projection
    );
}
```

---

## Validation

### Op

Enum des 14 opérateurs standards.

```java
package io.github.cyfko.filterql.core.validation;

public enum Op {
    EQ("=", "EQ"),
    NE("!=", "NE"),
    GT(">", "GT"),
    GTE(">=", "GTE"),
    LT("<", "LT"),
    LTE("<=", "LTE"),
    MATCHES("LIKE", "MATCHES"),
    NOT_MATCHES("NOT LIKE", "NOT_MATCHES"),
    IN("IN", "IN"),
    NOT_IN("NOT IN", "NOT_IN"),
    IS_NULL("IS NULL", "IS_NULL"),
    NOT_NULL("IS NOT NULL", "NOT_NULL"),
    RANGE("BETWEEN", "RANGE"),
    NOT_RANGE("NOT BETWEEN", "NOT_RANGE"),
    CUSTOM(null, null);
    
    /** Parse un opérateur depuis symbole ou code */
    public static Op fromString(String value);
    
    /** Vérifie si l'opérateur requiert une valeur */
    public boolean requiresValue();
    
    /** Vérifie si l'opérateur supporte des valeurs multiples */
    public boolean supportsMultipleValues();
}
```

---

### PropertyReference

Interface pour les références de propriété type-safe.

```java
package io.github.cyfko.filterql.core.validation;

public interface PropertyReference {
    
    /** Type Java de la propriété */
    Class<?> getType();
    
    /** Opérateurs supportés */
    Set<Op> getSupportedOperators();
    
    /** Type d'entité JPA cible */
    Class<?> getEntityType();
}
```

---

## Exceptions

### DSLSyntaxException

```java
package io.github.cyfko.filterql.core.exception;

public class DSLSyntaxException extends RuntimeException {
    public DSLSyntaxException(String message);
    public DSLSyntaxException(String message, Throwable cause);
}
```

Lancée pour :
- Expression vide ou composée uniquement d'espaces
- Parenthèses non équilibrées
- Opérateurs invalides
- Références de filtre non définies

### FilterValidationException

```java
package io.github.cyfko.filterql.core.exception;

public class FilterValidationException extends RuntimeException {
    public FilterValidationException(String message);
}
```

Lancée pour :
- Incompatibilité opérateur-type
- Valeur invalide pour l'opérateur
- Propriété non supportée

### FilterDefinitionException

```java
package io.github.cyfko.filterql.core.exception;

public class FilterDefinitionException extends RuntimeException {
    public FilterDefinitionException(String message);
}
```

Lancée pour :
- Référence de propriété null
- Code d'opérateur null ou vide
- Opérateur personnalisé non enregistré

---

## SPI d'Opérateurs Personnalisés

### CustomOperatorProvider

```java
package io.github.cyfko.filterql.core.spi;

/**
 * Contrat pour fournir des implémentations d'opérateurs de filtre personnalisés.
 * Les implémentations indiquent quels codes d'opérateurs elles supportent et fournissent
 * des méthodes pour résoudre les instances FilterDefinition en PredicateResolver exécutables.
 */
public interface CustomOperatorProvider {
    
    /**
     * Retourne l'ensemble des codes d'opérateurs supportés par ce provider.
     * Chaque code doit être unique parmi tous les providers enregistrés.
     * Utilisez la convention UPPER_SNAKE_CASE pour la cohérence.
     */
    Set<String> supportedOperators();
    
    /**
     * Résout une FilterDefinition en PredicateResolver pour la construction de requêtes.
     * La validation des valeurs doit être effectuée à l'intérieur du PredicateResolver retourné.
     */
    <P extends Enum<P> & PropertyReference> 
    PredicateResolver<?> toResolver(FilterDefinition<P> definition);
}
```

### Exemple d'implémentation

```java
public class StartsWithOperatorProvider implements CustomOperatorProvider {
    
    @Override
    public Set<String> supportedOperators() {
        return Set.of("STARTS_WITH");
    }

    @Override
    public <P extends Enum<P> & PropertyReference> 
    PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
        return (root, query, cb) -> {
            // Validation de la valeur au moment de l'exécution
            if (!(definition.value() instanceof String prefix) || prefix.isBlank()) {
                throw new IllegalArgumentException("STARTS_WITH requiert une valeur String non vide");
            }
            
            String fieldName = definition.ref().name().toLowerCase();
            return cb.like(root.get(fieldName), prefix + "%");
        };
    }
}
```

### OperatorProviderRegistry

```java
package io.github.cyfko.filterql.core.spi;

public final class OperatorProviderRegistry {
    
    /** Enregistre un fournisseur d'opérateur personnalisé */
    public static void register(CustomOperatorProvider provider);
    
    /** Récupère un provider par code d'opérateur (insensible à la casse) */
    public static Optional<CustomOperatorProvider> getProvider(String code);
    
    /** Vérifie si un code d'opérateur est enregistré */
    public static boolean isRegistered(String code);
    
    /** Désenregistre tous les opérateurs supportés par ce provider */
    public static void unregister(CustomOperatorProvider provider);
    
    /** Désenregistre des codes d'opérateurs spécifiques */
    public static void unregister(Set<String> codes);
    
    /** Efface toutes les enregistrements */
    public static void unregisterAll();
}
```

---

## Prochaines Étapes

- [Référence JPA Adapter](jpa-adapter) - JpaFilterContext et stratégies d'exécution
- [Référence Spring Adapter](spring-adapter) - Annotations et services Spring
