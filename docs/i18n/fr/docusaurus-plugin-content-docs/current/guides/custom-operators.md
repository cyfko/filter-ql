---
sidebar_position: 5
---

# Opérateurs Personnalisés

FilterQL permet d'étendre les 14 opérateurs standards avec des opérateurs personnalisés via l'interface SPI `CustomOperatorProvider`.

---

## Interface SPI

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
     * @return un ensemble non-null et non-vide de codes d'opérateurs
     */
    Set<String> supportedOperators();
    
    /**
     * Résout une FilterDefinition en PredicateResolver pour la construction de requêtes.
     * La validation des valeurs doit être effectuée à l'intérieur du PredicateResolver retourné.
     * @param definition la définition de filtre contenant les critères de filtrage
     * @return un PredicateResolver capable de produire le prédicat de requête
     */
    <P extends Enum<P> & PropertyReference> 
    PredicateResolver<?> toResolver(FilterDefinition<P> definition);
}
```

---

## Enregistrement d'un Opérateur

### Via OperatorProviderRegistry

```java
import io.github.cyfko.filterql.core.spi.OperatorProviderRegistry;
import io.github.cyfko.filterql.core.spi.CustomOperatorProvider;

// Enregistrer au démarrage de l'application
OperatorProviderRegistry.register(new SoundexOperatorProvider());
```

### Implémentation Exemple : SOUNDEX

```java
public class SoundexOperatorProvider implements CustomOperatorProvider {
    
    @Override
    public Set<String> supportedOperators() {
        return Set.of("SOUNDEX");
    }
    
    @Override
    public <P extends Enum<P> & PropertyReference> 
    PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
        return (root, query, cb) -> {
            // Validation de la valeur au moment de l'exécution
            Object value = definition.value();
            if (value == null) {
                throw new IllegalArgumentException("La valeur SOUNDEX ne peut pas être null");
            }
            if (!(value instanceof String strValue)) {
                throw new IllegalArgumentException("SOUNDEX requiert une valeur String");
            }
            if (strValue.isBlank()) {
                throw new IllegalArgumentException("La valeur SOUNDEX ne peut pas être vide");
            }
            
            String fieldName = definition.ref().name().toLowerCase();
            return cb.equal(
                cb.function("SOUNDEX", String.class, root.get(fieldName)),
                cb.function("SOUNDEX", String.class, cb.literal(strValue))
            );
        };
    }
}
```

---

## Exemples d'Opérateurs Personnalisés

### Recherche Full-Text

```java
public class FullTextOperatorProvider implements CustomOperatorProvider {
    
    @Override
    public Set<String> supportedOperators() {
        return Set.of("FULLTEXT");
    }
    
    @Override
    public <P extends Enum<P> & PropertyReference> 
    PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
        return (root, query, cb) -> {
            Object value = definition.value();
            if (!(value instanceof String searchQuery)) {
                throw new IllegalArgumentException("FULLTEXT requiert une requête String");
            }
            
            // Nettoyer et préparer la requête full-text
            String cleanedQuery = searchQuery.trim().replaceAll("\\s+", " & ");
            String fieldName = definition.ref().name().toLowerCase();
            
            // PostgreSQL ts_vector / ts_query
            return cb.isTrue(
                cb.function(
                    "to_tsvector",
                    Boolean.class,
                    cb.literal("french"),
                    root.get(fieldName)
                ).in(
                    cb.function(
                        "to_tsquery",
                        Object.class,
                        cb.literal("french"),
                        cb.literal(cleanedQuery)
                    )
                )
            );
        };
    }
}
```

### Distance Géographique

```java
public class GeoDistanceOperatorProvider implements CustomOperatorProvider {
    
    @Override
    public Set<String> supportedOperators() {
        return Set.of("GEO_WITHIN");
    }
    
    @Override
    public <P extends Enum<P> & PropertyReference> 
    PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
        return (root, query, cb) -> {
            Object value = definition.value();
            if (!(value instanceof Map<?, ?> params)) {
                throw new IllegalArgumentException("GEO_WITHIN requiert un Map avec lat, lng, radiusKm");
            }
            
            Double lat = (Double) params.get("lat");
            Double lng = (Double) params.get("lng");
            Double radiusKm = (Double) params.get("radiusKm");
            
            if (lat == null || lng == null || radiusKm == null) {
                throw new IllegalArgumentException("lat, lng et radiusKm sont requis");
            }
            
            // Formule Haversine simplifiée
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
}
```

### Opérateur JSON (PostgreSQL)

```java
public class JsonContainsOperatorProvider implements CustomOperatorProvider {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public Set<String> supportedOperators() {
        return Set.of("JSON_CONTAINS");
    }
    
    @Override
    public <P extends Enum<P> & PropertyReference> 
    PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
        return (root, query, cb) -> {
            Object value = definition.value();
            if (!(value instanceof Map<?, ?> jsonValue)) {
                throw new IllegalArgumentException("JSON_CONTAINS requiert une valeur Map");
            }
            
            // Convertir en chaîne JSON
            String jsonString;
            try {
                jsonString = objectMapper.writeValueAsString(jsonValue);
            } catch (Exception e) {
                throw new IllegalArgumentException("Échec de la sérialisation en JSON", e);
            }
            
            String fieldName = definition.ref().name().toLowerCase();
            // Opérateur PostgreSQL @>
            return cb.isTrue(
                cb.function(
                    "jsonb_contains",
                    Boolean.class,
                    root.get(fieldName),
                    cb.literal(jsonString)
                )
            );
        };
    }
}
```

---

## Enregistrement au Démarrage Spring

```java
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import io.github.cyfko.filterql.core.spi.OperatorProviderRegistry;

@Configuration
public class FilterQlOperatorConfig {
    
    @PostConstruct
    public void registerCustomOperators() {
        OperatorProviderRegistry.register(new SoundexOperatorProvider());
        OperatorProviderRegistry.register(new FullTextOperatorProvider());
        OperatorProviderRegistry.register(new GeoDistanceOperatorProvider());
        OperatorProviderRegistry.register(new JsonContainsOperatorProvider());
    }
}
```

---

## Utilisation

Une fois enregistré, l'opérateur personnalisé s'utilise comme un opérateur standard :

```java
// Utilisation avec code string
var soundexFilter = new FilterDefinition<>(
    UserPropertyRef.FULL_NAME,
    "SOUNDEX",  // Code de l'opérateur personnalisé
    "Smith"
);

// Dans une requête complète
FilterRequest<UserPropertyRef> request = FilterRequest.<UserPropertyRef>builder()
    .filter("soundexName", UserPropertyRef.FULL_NAME, "SOUNDEX", "Smith")
    .filter("active", UserPropertyRef.STATUS, Op.EQ, UserStatus.ACTIVE)
    .combineWith("soundexName & active")
    .build();
```

---

## Bonnes Pratiques

| Aspect | Recommandation |
|--------|----------------|
| **Nommage** | Utiliser des codes en MAJUSCULES descriptifs (SOUNDEX, FULLTEXT, GEO_WITHIN) |
| **Validation** | Valider rigoureusement dans `toResolver()` au moment de l'exécution |
| **Messages d'erreur** | Fournir des messages clairs et exploitables |
| **Documentation** | Documenter les paramètres attendus et le comportement |
| **Tests** | Écrire des tests unitaires pour chaque opérateur |

---

## Prochaines Étapes

- [Référence Core](../reference/core) - API complète du module core
- [Référence JPA Adapter](../reference/jpa-adapter) - PredicateResolver en détail
