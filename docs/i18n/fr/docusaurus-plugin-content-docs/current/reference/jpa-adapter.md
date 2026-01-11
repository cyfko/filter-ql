---
sidebar_position: 2
---

# Référence JPA Adapter

Documentation de référence complète pour le module `filterql-adapter-jpa` (version 2.0.0).

---

## Coordonnées Maven

```xml
<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>filterql-adapter-jpa</artifactId>
    <version>2.0.0</version>
</dependency>

<!-- Dépendance externe requise -->
<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>projection-metamodel-processor</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

---

## Structure des Packages

```
io.github.cyfko.filterql.jpa
├── JpaFilterContext           # Implémentation principale de FilterContext
├── JpaCondition               # Wrapper de condition JPA
├── mappings/
│   ├── CustomOperatorResolver   # Gestionnaire centralisé d'opérateurs personnalisés
│   ├── PredicateResolverMapping # Mapping de prédicat par propriété
│   └── InstanceResolver        # Résolution de beans IoC
├── strategies/
│   ├── MultiQueryFetchStrategy      # Stratégie DTO avec batch (RECOMMANDÉE)
│   ├── TypedMultiQueryFetchStrategy # Variante typée
│   ├── FullEntityFetchStrategy      # Récupération d'entités complètes
│   └── CountStrategy                # Requête de comptage optimisée
├── projection/
│   ├── ProjectionUtils         # Utilitaires de projection
│   └── CollectionPaginationParser # Parsing de pagination inline
└── utils/
    └── PathResolverUtils       # Navigation de chemin JPA
```

---

## JpaFilterContext

Implémentation de `FilterContext` pour JPA Criteria API.

### Constructeurs

```java
package io.github.cyfko.filterql.jpa;

public class JpaFilterContext<P extends Enum<P> & PropertyReference> 
    implements FilterContext {
    
    /**
     * Constructeur avec configuration par défaut.
     * 
     * @param enumClass classe de l'enum PropertyReference
     * @param mappingBuilder fonction de mapping propriété → chemin/resolver
     */
    public JpaFilterContext(
        Class<P> enumClass, 
        Function<P, Object> mappingBuilder
    );
    
    /**
     * Constructeur avec configuration personnalisée.
     * 
     * @param enumClass classe de l'enum PropertyReference
     * @param mappingBuilder fonction de mapping
     * @param filterConfig configuration du comportement de filtrage
     */
    public JpaFilterContext(
        Class<P> enumClass,
        Function<P, Object> mappingBuilder,
        FilterConfig filterConfig
    );
}
```

### Fonction de Mapping

Le second paramètre `mappingBuilder` est une `Function<P, Object>` qui retourne :

- **`String`** : Chemin de propriété JPA direct (ex: `"username"`, `"address.city"`)
- **`PredicateResolverMapping<E>`** : Logique de prédicat personnalisée

```java
JpaFilterContext<UserPropertyRef> context = new JpaFilterContext<>(
    UserPropertyRef.class,
    ref -> switch (ref) {
        // Mappings simples (String)
        case USERNAME -> "username";
        case EMAIL -> "email";
        case AGE -> "age";
        case CITY -> "address.city.name";  // Chemin imbriqué
        
        // Mapping personnalisé (PredicateResolverMapping)
        case FULL_NAME -> (PredicateResolverMapping<User>) (op, args) -> (root, query, cb) -> {
            String search = (String) args[0];
            return cb.or(
                cb.like(cb.lower(root.get("firstName")), "%" + search.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("lastName")), "%" + search.toLowerCase() + "%")
            );
        };
    }
);
```

### Méthodes

```java
/**
 * Retourne la classe enum de référence de propriété.
 */
public Class<P> getPropertyRefClass();

/**
 * Remplace la fonction de mapping.
 * 
 * @param mappingBuilder nouvelle fonction de mapping
 * @return fonction précédente
 */
public Function<P, Object> setMappingBuilder(Function<P, Object> mappingBuilder);
```

---

## PredicateResolverMapping

Interface pour la logique de prédicat personnalisée.

```java
package io.github.cyfko.filterql.jpa.mappings;

import io.github.cyfko.filterql.core.spi.PredicateResolver;

@FunctionalInterface
public interface PredicateResolverMapping<E> extends ReferenceMapping<E> {
    
    /**
     * Résout un PredicateResolver pour le code opérateur et les arguments donnés.
     * 
     * @param op le code opérateur du filtre (ex: "EQ", "LIKE", "SOUNDEX")
     * @param args les arguments de l'opérateur du filtre
     * @return le PredicateResolver pour la génération différée du prédicat
     */
    PredicateResolver<E> map(String op, Object[] args);
}
```

### Exemples d'Utilisation

#### Recherche Multi-Champs

```java
case FULL_NAME -> (PredicateResolverMapping<User>) (op, args) -> (root, query, cb) -> {
    String search = (String) args[0];
    String pattern = "%" + search.toLowerCase() + "%";
    return cb.or(
        cb.like(cb.lower(root.get("firstName")), pattern),
        cb.like(cb.lower(root.get("lastName")), pattern),
        cb.like(cb.lower(cb.concat(
            cb.concat(root.get("firstName"), " "),
            root.get("lastName")
        )), pattern)
    );
};
```

#### Calcul de Plage d'Âge

```java
case AGE_RANGE -> (PredicateResolverMapping<User>) (op, args) -> (root, query, cb) -> {
    List<?> range = (List<?>) args[0];
    int minAge = (int) range.get(0);
    int maxAge = (int) range.get(1);
    LocalDate now = LocalDate.now();
    LocalDate maxBirthDate = now.minusYears(minAge);
    LocalDate minBirthDate = now.minusYears(maxAge + 1);
    return cb.between(root.get("birthDate"), minBirthDate, maxBirthDate);
};
```

#### Sous-Requête

```java
case HAS_ORDERS -> (PredicateResolverMapping<User>) (op, args) -> (root, query, cb) -> {
    Subquery<Long> subquery = query.subquery(Long.class);
    Root<Order> orderRoot = subquery.from(Order.class);
    subquery.select(cb.count(orderRoot))
            .where(cb.equal(orderRoot.get("user"), root));
    return cb.greaterThan(subquery, 0L);
};
```

---

## CustomOperatorResolver

Interface centralisée pour gérer les opérateurs personnalisés sur toutes les propriétés. Ajoutée via la méthode fluide `withCustomOperatorResolver()`.

```java
package io.github.cyfko.filterql.jpa.mappings;

@FunctionalInterface
public interface CustomOperatorResolver<P extends Enum<P> & PropertyReference> {
    
    /**
     * Résout un opérateur personnalisé vers un PredicateResolver.
     *
     * @param ref  la référence de propriété filtrée
     * @param op   le code de l'opérateur (ex: "SOUNDEX", "GEO_WITHIN")
     * @param args les arguments du filtre
     * @return PredicateResolver pour gérer cette opération, ou null pour déléguer au défaut
     */
    PredicateResolver<?> resolve(P ref, String op, Object[] args);
}
```

### Flux de Résolution

1. `CustomOperatorResolver.resolve()` est appelé en premier
2. S'il retourne un `PredicateResolver` non-null, ce resolver est utilisé
3. S'il retourne `null`, le mécanisme par défaut (path ou `PredicateResolverMapping`) est utilisé

### Utilisation

```java
JpaFilterContext<UserProperty> context = new JpaFilterContext<>(
        UserProperty.class, 
        mappingBuilder
    ).withCustomOperatorResolver((ref, op, args) -> {
        return switch (op) {
            case "SOUNDEX" -> handleSoundex(ref, args);
            case "GEO_WITHIN" -> handleGeoWithin(ref, args);
            default -> null;  // Utiliser le mécanisme par défaut
        };
    });
```

:::tip Quand Utiliser
- **CustomOperatorResolver** : Pour les opérateurs qui s'appliquent à plusieurs propriétés (SOUNDEX, FULL_TEXT, GEO_WITHIN)
- **PredicateResolverMapping** : Pour une logique spécifique à une propriété où chaque propriété a un comportement unique
:::

---

## Stratégies d'Exécution

### MultiQueryFetchStrategy (RECOMMANDÉE)

Stratégie DTO avec batch fetching pour les collections. Évite les problèmes N+1.

```java
package io.github.cyfko.filterql.jpa.strategies;

public class MultiQueryFetchStrategy implements ExecutionStrategy<List<Map<String, Object>>> {
    
    /**
     * Constructeur avec classe de projection.
     * 
     * @param projectionClass classe DTO annotée @Projection
     */
    public MultiQueryFetchStrategy(Class<?> projectionClass);
    
    /**
     * Constructeur avec résolveur d'instances IoC.
     * 
     * @param projectionClass classe DTO
     * @param instanceResolver résolveur pour champs @Computed
     */
    public MultiQueryFetchStrategy(
        Class<?> projectionClass, 
        InstanceResolver instanceResolver
    );
}
```

#### Utilisation

```java
FilterRequest<UserPropertyRef> request = FilterRequest.<UserPropertyRef>builder()
    .filter("active", UserPropertyRef.STATUS, Op.EQ, UserStatus.ACTIVE)
    .combineWith("active")
    .projection("id", "username", "email", "orders[size=5].id,total")
    .pagination(new Pagination(0, 20, null))
    .build();

MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(UserDTO.class);
QueryExecutor<List<Map<String, Object>>> executor = filterQuery.toExecutor(request);
List<Map<String, Object>> results = executor.executeWith(em, strategy);
```

---

### FullEntityFetchStrategy

Récupère des entités complètes sans projection.

```java
package io.github.cyfko.filterql.jpa.strategies;

public class FullEntityFetchStrategy<E> implements ExecutionStrategy<List<E>> {
    
    /**
     * @param entityClass classe de l'entité JPA
     */
    public FullEntityFetchStrategy(Class<E> entityClass);
}
```

#### Utilisation

```java
// Requête SANS projection
FilterRequest<UserPropertyRef> request = FilterRequest.<UserPropertyRef>builder()
    .filter("active", UserPropertyRef.STATUS, Op.EQ, UserStatus.ACTIVE)
    .combineWith("active")
    // Pas de .projection() → entités complètes
    .build();

FullEntityFetchStrategy<User> strategy = new FullEntityFetchStrategy<>(User.class);
QueryExecutor<List<User>> executor = filterQuery.toExecutor(request);
List<User> users = executor.executeWith(em, strategy);
```

---

### CountStrategy

Requête de comptage optimisée.

```java
package io.github.cyfko.filterql.jpa.strategies;

public class CountStrategy implements ExecutionStrategy<Long> {
    
    /**
     * @param projectionClass classe DTO pour dériver l'entité
     */
    public CountStrategy(Class<?> projectionClass);
}
```

#### Utilisation

```java
CountStrategy strategy = new CountStrategy(UserDTO.class);
QueryExecutor<Long> executor = filterQuery.toExecutor(request);
Long count = executor.executeWith(em, strategy);
```

---

### Comparaison des Stratégies

| Stratégie | Cas d'Usage | Retour | Projection | Collections |
|-----------|-------------|--------|------------|-------------|
| `MultiQueryFetchStrategy` | DTO avec collections | `List<Map<String, Object>>` | ✅ | ✅ Batch |
| `FullEntityFetchStrategy` | Entités complètes | `List<E>` | ❌ | Via lazy loading |
| `CountStrategy` | Comptage uniquement | `Long` | ❌ | N/A |

---

## FilterConfig

Configuration du comportement de filtrage.

```java
package io.github.cyfko.filterql.core.config;

public record FilterConfig(
    boolean ignoreCase,           // Comparaisons insensibles à la casse
    NullHandling nullHandling,    // Tri des nulls
    EnumMatching enumMatching,    // Stratégie de matching enum
    StringNormalization stringNormalization  // Normalisation des chaînes
) {
    
    public static Builder builder();
    
    public enum NullHandling {
        NULLS_FIRST,
        NULLS_LAST,
        NATIVE      // Comportement par défaut de la DB
    }
    
    public enum EnumMatching {
        NAME,       // Matcher par Enum.name()
        ORDINAL,    // Matcher par Enum.ordinal()
        STRING      // Matcher par toString()
    }
    
    public enum StringNormalization {
        NONE,       // Pas de normalisation
        TRIM,       // Supprimer espaces
        LOWERCASE,  // Convertir en minuscules
        UPPERCASE   // Convertir en majuscules
    }
}
```

#### Exemple

```java
FilterConfig config = FilterConfig.builder()
    .ignoreCase(true)
    .nullHandling(FilterConfig.NullHandling.NULLS_LAST)
    .enumMatching(FilterConfig.EnumMatching.NAME)
    .stringNormalization(FilterConfig.StringNormalization.TRIM)
    .build();

JpaFilterContext<UserPropertyRef> context = new JpaFilterContext<>(
    UserPropertyRef.class,
    mappingFunction,
    config
);
```

---

## InstanceResolver

Interface pour la résolution de beans IoC (champs `@Computed`).

```java
package io.github.cyfko.filterql.jpa.mappings;

public interface InstanceResolver {
    
    /**
     * Résout une instance de provider depuis le conteneur IoC.
     * 
     * @param providerClass classe du provider
     * @return instance du provider
     */
    <T> T resolve(Class<T> providerClass);
}
```

---

## PathResolverUtils

Utilitaires pour la navigation de chemin JPA.

```java
package io.github.cyfko.filterql.jpa.utils;

public final class PathResolverUtils {
    
    /**
     * Résout un chemin imbriqué en Path JPA.
     * 
     * @param root racine de l'entité
     * @param path chemin séparé par points (ex: "address.city.name")
     * @return Path JPA résolu
     */
    public static <E> Path<?> resolvePath(Root<E> root, String path);
    
    /**
     * Résout avec type générique.
     */
    public static <E, T> Path<T> resolvePath(Root<E> root, String path, Class<T> type);
}
```

---

## Annotations Externes

:::note Dépendances Externes
Les annotations suivantes proviennent de [projection-spec](https://github.com/cyfko/projection-spec) et sont implémentées par [projection-metamodel-processor](https://github.com/cyfko/jpa-metamodel-processor).
:::

### @Projection

Définit une classe DTO avec mapping vers une entité JPA.

```java
import io.github.cyfko.projection.Projection;

@Projection(entity = User.class)
public class UserDTO {
    private Long id;
    private String username;
    // ...
}
```

### @Projected

Personnalise le mapping d'un champ DTO.

```java
import io.github.cyfko.projection.Projected;

@Projection(entity = User.class)
public class UserDTO {
    
    @Projected(from = "firstName")  // Mapping explicite
    private String name;
    
    @Projected(from = "address.city.name")  // Chemin imbriqué
    private String cityName;
}
```

### @Computed

Définit un champ calculé dynamiquement.

```java
import io.github.cyfko.projection.Computed;

@Projection(entity = User.class)
public class UserDTO {
    
    @Computed(provider = AgeCalculator.class, method = "calculateAge")
    private Integer age;
}
```

### @Provider

Marque une classe comme fournisseur de valeurs calculées.

```java
import io.github.cyfko.projection.Provider;
import org.springframework.stereotype.Component;

@Provider
@Component
public class AgeCalculator {
    
    public Integer calculateAge(User user) {
        if (user.getBirthDate() == null) return null;
        return Period.between(user.getBirthDate(), LocalDate.now()).getYears();
    }
}
```

---

## Prochaines Étapes

- [Référence Spring Adapter](spring-adapter) - Intégration Spring Boot
- [Guide Projection](../guides/projection) - Syntaxe de projection avancée
