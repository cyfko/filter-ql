---
sidebar_position: 5
---

# Opérateurs Personnalisés

FilterQL fournit 14 opérateurs standards pour la plupart des besoins de filtrage. Pour les cas d'usage avancés nécessitant une logique de filtre personnalisée, l'**Adaptateur JPA** offre deux approches complémentaires :

1. **`CustomOperatorResolver`** - Gestionnaire centralisé pour les opérateurs qui s'appliquent à plusieurs propriétés
2. **`PredicateResolverMapping`** - Logique personnalisée par propriété pour un comportement spécifique

:::tip Syntaxe Simplifiée Spring
Avec l'**Adaptateur Spring** et `@ExposedAs`, vous pouvez utiliser une syntaxe plus simple :

```java
@ExposedAs(value = "FULL_NAME", operators = {Op.MATCHES})
public static PredicateResolver<User> fullNameSearch(String op, Object[] args) {
    return (root, query, cb) -> { /* votre logique */ };
}
```

Voir [Adaptateur Spring - Champs Virtuels](../reference/spring-adapter#champs-virtuels) pour les détails.
:::

---

## Approche 1 : CustomOperatorResolver

L'interface `CustomOperatorResolver` fournit un moyen centralisé de gérer les opérateurs personnalisés. Elle est appelée avant le mécanisme de résolution par défaut, vous permettant d'intercepter n'importe quel opérateur pour n'importe quelle propriété.

**Idéal pour :** Les opérateurs comme SOUNDEX, FULL_TEXT, GEO_WITHIN qui s'appliquent à plusieurs propriétés avec une logique similaire.

### Interface

```java
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

### Utilisation de Base

```java
JpaFilterContext<UserProperty> context = new JpaFilterContext<>(
        UserProperty.class, 
        mappingBuilder
    ).withCustomOperatorResolver((ref, op, args) -> {
        // Retourner null pour déléguer au mécanisme par défaut
        if (!"SOUNDEX".equals(op)) {
            return null;
        }
        
        // Gérer l'opérateur SOUNDEX pour les propriétés applicables
        String fieldPath = switch (ref) {
            case FIRST_NAME -> "firstName";
            case LAST_NAME -> "lastName";
            default -> throw new IllegalArgumentException(
                "SOUNDEX non supporté pour " + ref);
        };
        
        return (root, query, cb) -> cb.equal(
            cb.function("SOUNDEX", String.class, root.get(fieldPath)),
            cb.function("SOUNDEX", String.class, cb.literal((String) args[0]))
        );
    });
```

### Plusieurs Opérateurs Personnalisés

```java
CustomOperatorResolver<UserProperty> resolver = (ref, op, args) -> {
    return switch (op) {
        case "SOUNDEX" -> handleSoundex(ref, args);
        case "LEVENSHTEIN" -> handleLevenshtein(ref, args);
        case "GEO_WITHIN" -> handleGeoWithin(ref, args);
        case "FULL_TEXT" -> handleFullText(ref, args);
        default -> null;  // Déléguer au mécanisme par défaut
    };
};

JpaFilterContext<UserProperty> context = new JpaFilterContext<>(
        UserProperty.class, 
        mappingBuilder
    ).withCustomOperatorResolver(resolver);
```

### Surcharger les Opérateurs Standards

Vous pouvez surcharger les opérateurs standards pour des propriétés spécifiques :

```java
CustomOperatorResolver<UserProperty> resolver = (ref, op, args) -> {
    // Rendre MATCHES insensible à la casse pour EMAIL
    if (ref == UserProperty.EMAIL && "MATCHES".equals(op)) {
        return (root, query, cb) -> {
            String pattern = ((String) args[0]).toLowerCase();
            return cb.like(cb.lower(root.get("email")), pattern);
        };
    }
    return null;
};
```

---

## Approche 2 : PredicateResolverMapping

Les opérateurs personnalisés sont implémentés directement dans la fonction de mapping de `JpaFilterContext` en utilisant `PredicateResolverMapping<E>`. Cette approche offre un contrôle total sur la génération des prédicats.

**Idéal pour :** La logique spécifique à une propriété comme la recherche multi-champs (FULL_NAME), les champs calculés, ou les sous-requêtes uniques à une seule propriété.

### Interface

```java
package io.github.cyfko.filterql.jpa.mappings;

@FunctionalInterface
public interface PredicateResolverMapping<E> extends ReferenceMapping<E> {
    
    /**
     * Résout un PredicateResolver à partir du code opérateur et des arguments.
     *
     * @param op l'opérateur de filtre à appliquer (ex: "EQ", "LIKE", "SOUNDEX")
     * @param args les arguments de l'opérateur du filtre
     * @return le PredicateResolver pour la génération différée du prédicat
     */
    PredicateResolver<E> map(String op, Object[] args);
}
```

---

## Exemples d'Implémentation

### Opérateur SOUNDEX

```java
// Définir dans le mapping JpaFilterContext
JpaFilterContext<UserPropertyRef> context = new JpaFilterContext<>(
    UserPropertyRef.class,
    ref -> switch (ref) {
        case USERNAME -> "username";  // Mapping simple
        case EMAIL -> "email";
        
        // Opérateur SOUNDEX personnalisé
        case LAST_NAME -> new PredicateResolverMapping<User>() {
            @Override
            public PredicateResolver<User> map(String op, Object[] args) {
                return (root, query, cb) -> {
                    if (!"SOUNDEX".equals(op)) {
                        // Comportement par défaut pour les autres opérateurs
                        return cb.equal(root.get("lastName"), args[0]);
                    }
                    
                    String searchValue = (String) args[0];
                    if (searchValue == null || searchValue.isBlank()) {
                        throw new IllegalArgumentException("SOUNDEX nécessite une valeur non vide");
                    }
                    
                    return cb.equal(
                        cb.function("SOUNDEX", String.class, root.get("lastName")),
                        cb.function("SOUNDEX", String.class, cb.literal(searchValue))
                    );
                };
            }
        };
    }
);
```

### Recherche Full-Text

```java
case DESCRIPTION -> new PredicateResolverMapping<Product>() {
    @Override
    public PredicateResolver<Product> map(String op, Object[] args) {
        return (root, query, cb) -> {
            if (!"FULLTEXT".equals(op)) {
                return cb.like(root.get("description"), "%" + args[0] + "%");
            }
            
            String searchQuery = (String) args[0];
            String cleanedQuery = searchQuery.trim().replaceAll("\\s+", " & ");
            
            // PostgreSQL ts_vector / ts_query
            return cb.isTrue(
                cb.function(
                    "to_tsvector",
                    Boolean.class,
                    cb.literal("english"),
                    root.get("description")
                ).in(
                    cb.function(
                        "to_tsquery",
                        Object.class,
                        cb.literal("english"),
                        cb.literal(cleanedQuery)
                    )
                )
            );
        };
    }
};
```

### Distance Géographique (GEO_WITHIN)

```java
case LOCATION -> new PredicateResolverMapping<Store>() {
    @Override
    public PredicateResolver<Store> map(String op, Object[] args) {
        return (root, query, cb) -> {
            if (!"GEO_WITHIN".equals(op)) {
                throw new IllegalArgumentException("LOCATION supporte uniquement l'opérateur GEO_WITHIN");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Double> params = (Map<String, Double>) args[0];
            
            Double lat = params.get("lat");
            Double lng = params.get("lng");
            Double radiusKm = params.get("radiusKm");
            
            if (lat == null || lng == null || radiusKm == null) {
                throw new IllegalArgumentException("lat, lng et radiusKm sont requis");
            }
            
            // Fonction de distance Haversine (spécifique à la base de données)
            Expression<Double> distance = cb.function(
                "haversine_distance",
                Double.class,
                root.get("latitude"),
                root.get("longitude"),
                cb.literal(lat),
                cb.literal(lng)
            );
            
            return cb.lessThanOrEqualTo(distance, radiusKm);
        };
    }
};
```

---

## Recherche Multi-Champs

Un cas d'usage courant est la recherche sur plusieurs champs :

```java
case FULL_NAME -> new PredicateResolverMapping<User>() {
    @Override
    public PredicateResolver<User> map(String op, Object[] args) {
        return (root, query, cb) -> {
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
    }
};
```

---

## Utilisation dans FilterRequest

Une fois configurés dans `JpaFilterContext`, les opérateurs personnalisés s'utilisent comme les opérateurs standards :

```java
// Utilisation de l'opérateur SOUNDEX personnalisé
FilterRequest<UserPropertyRef> request = FilterRequest.<UserPropertyRef>builder()
    .filter("soundexName", UserPropertyRef.LAST_NAME, "SOUNDEX", "Smith")
    .filter("active", UserPropertyRef.STATUS, Op.EQ, UserStatus.ACTIVE)
    .combineWith("soundexName & active")
    .build();

// Utilisation de l'opérateur GEO_WITHIN personnalisé
FilterRequest<StorePropertyRef> geoRequest = FilterRequest.<StorePropertyRef>builder()
    .filter("nearby", StorePropertyRef.LOCATION, "GEO_WITHIN", 
        Map.of("lat", 48.8566, "lng", 2.3522, "radiusKm", 10.0))
    .combineWith("nearby")
    .build();
```

---

## Bonnes Pratiques

| Aspect | Recommandation |
|--------|----------------|
| **Nommage** | Utiliser des codes descriptifs en MAJUSCULES (SOUNDEX, FULLTEXT, GEO_WITHIN) |
| **Validation** | Valider les arguments dans le PredicateResolver |
| **Messages d'erreur** | Fournir des messages clairs et exploitables |
| **Comportement par défaut** | Gérer les opérateurs standards en fallback |
| **Tests** | Écrire des tests unitaires pour chaque mapping personnalisé |
| **Documentation** | Documenter les types de paramètres attendus et les formats |

---

## Méthodes Utilitaires

Pour des opérateurs personnalisés réutilisables, créez des méthodes statiques :

```java
public final class CustomMappings {
    
    private CustomMappings() {}
    
    /**
     * Crée un mapping SOUNDEX pour n'importe quel champ texte.
     */
    public static <E> PredicateResolverMapping<E> soundexMapping(String fieldName) {
        return new PredicateResolverMapping<>() {
            @Override
            public PredicateResolver<E> map(String op, Object[] args) {
                return (root, query, cb) -> {
                    String searchValue = (String) args[0];
                    return cb.equal(
                        cb.function("SOUNDEX", String.class, root.get(fieldName)),
                        cb.function("SOUNDEX", String.class, cb.literal(searchValue))
                    );
                };
            }
        };
    }
    
    /**
     * Crée un mapping LIKE insensible à la casse.
     */
    public static <E> PredicateResolverMapping<E> caseInsensitiveLike(String fieldName) {
        return new PredicateResolverMapping<>() {
            @Override
            public PredicateResolver<E> map(String op, Object[] args) {
                return (root, query, cb) -> {
                    String pattern = "%" + ((String) args[0]).toLowerCase() + "%";
                    return cb.like(cb.lower(root.get(fieldName)), pattern);
                };
            }
        };
    }
}

// Utilisation
JpaFilterContext<UserPropertyRef> context = new JpaFilterContext<>(
    UserPropertyRef.class,
    ref -> switch (ref) {
        case LAST_NAME -> CustomMappings.soundexMapping("lastName");
        case USERNAME -> CustomMappings.caseInsensitiveLike("username");
        default -> ref.name().toLowerCase();
    }
);
```

---

## Prochaines Étapes

- [Référence Core](../reference/core) - API complète du module core
- [Référence Adaptateur JPA](../reference/jpa-adapter) - PredicateResolverMapping en détail
