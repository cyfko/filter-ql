---
sidebar_position: 4
---

# Guide Avancé

Cas complexes, personnalisation, et techniques avancées pour les utilisateurs expérimentés.

---

## Projections Avancées {#projections}

Les annotations `@Projected` et `@Computed` permettent un contrôle fin sur le mapping entre entité et DTO.

### @Projected : Renommer ou Mapper un Champ

Quand le nom du champ DTO diffère du nom de la propriété entité :

```java
@Entity
public class Order {
    @Id private Long id;
    private String orderNumber;  // Nom dans l'entité
    
    @OneToMany(mappedBy = "order")
    private List<OrderItem> items;
}

@Projection(from = Order.class)
@Exposure(value = "orders", basePath = "/api")
public class OrderDTO {

    @Projected(from = "id")
    private Long id;

    @Projected(from = "orderNumber")  // Mappe orderNumber → number
    @ExposedAs(value = "ORDER_NUMBER", operators = {Op.EQ, Op.MATCHES})
    private String number;

    @Projected(from = "items")  // Mappe items → orderItems
    private List<OrderItemDTO> orderItems;

    // Getters...
}
```

**Cas d'usage :**
- Renommer des propriétés pour l'API (`orderNumber` → `number`)
- Mapper des collections vers des noms différents
- Accéder à des propriétés imbriquées (`@Projected(from = "department.name")`)

### @Computed : Champs Calculés

Pour des champs qui n'existent pas dans l'entité mais sont calculés à partir d'autres propriétés :

```java
@Projection(from = User.class, providers = @Provider(UserUtils.class))
@Exposure(value = "users", basePath = "/api")
public class UserDTO {

    @Projected(from = "id")
    private Long id;

    @Projected(from = "name")
    private String name;

    @Projected(from = "orders")
    private List<OrderDTO> orders;

    /**
     * Champ calculé à partir de id et name.
     * La méthode de calcul est dans UserUtils.
     */
    @Computed(dependsOn = {"id", "name"})
    private String keyIdentifier;

    /**
     * Champ calculé complexe retournant un objet.
     */
    @Computed(dependsOn = {"id"})
    private UserHistory lastHistory;

    // Getters...

    public static class UserHistory {
        private String year;
        private String[] comments;
        // ...
    }
}
```

**Classe Provider avec les méthodes de calcul :**

```java
public class UserUtils {
    
    /**
     * Appelé automatiquement pour calculer keyIdentifier.
     * Le nom de méthode suit la convention : get + NomDuChamp
     */
    public static String getKeyIdentifier(Long id, String name) {
        return id + "-" + name;
    }

    /**
     * Appelé automatiquement pour calculer lastHistory.
     */
    public static UserDTO.UserHistory getLastHistory(Long id) {
        // Logique pour récupérer l'historique...
        return new UserDTO.UserHistory("2024", new String[]{"Created", "Updated"});
    }
}
```

**Règles importantes :**
- `@Provider` : Déclare la classe contenant les méthodes de calcul
- `dependsOn` : Liste les champs de l'entité nécessaires au calcul
- La méthode doit être `public static` et nommée `get<NomDuChamp>`
- Les paramètres correspondent aux champs listés dans `dependsOn`

---

## Relations JPA {#relations}

FilterQL peut filtrer sur des propriétés de relations (one-to-one, many-to-one, etc.).

### Exemple : User → Address

**Entités :**

```java
@Entity
public class User {
    @Id private Long id;
    private String name;
    
    @ManyToOne
    private Address address;
}

@Entity
public class Address {
    @Id private Long id;
    private String city;
    private String country;
}
```

**DTO avec relation :**

```java
@Projection(from = User.class)
@Exposure(value = "users", basePath = "/api")
public class UserDTO {

    @ExposedAs(value = "NAME", operators = {Op.EQ, Op.MATCHES})
    private String name;

    private AddressDTO address;  // DTO imbriqué
}

@Projection(from = Address.class)
public class AddressDTO {

    @ExposedAs(value = "CITY", operators = {Op.EQ, Op.MATCHES, Op.IN})
    private String city;

    @ExposedAs(value = "COUNTRY", operators = {Op.EQ, Op.IN})
    private String country;
}
```

**Requête :**

```json
{
  "filters": {
    "inParis": { "ref": "CITY", "op": "EQ", "value": "Paris" }
  },
  "combineWith": "inParis",
  "projection": ["name", "address.city,country"]
}
```

### Mapping Personnalisé avec JpaFilterContext

Si la convention de nommage ne suffit pas, utilisez un mapping explicite :

```java
@Bean
public JpaFilterContext<UserProperty> userFilterContext(EntityManager em) {
    return new JpaFilterContext<>(
        UserProperty.class,
        (root, prop) -> switch (prop) {
            case NAME -> root.get("name");
            case ADDRESS_CITY -> root.get("address").get("city");
            case ADDRESS_COUNTRY -> root.get("address").get("country");
        }
    );
}
```

---

## Collections avec Pagination Inline {#collections}

Filtrer les éléments d'une collection et paginer le résultat directement dans la projection.

### Exemple : User → Books

**Entité :**

```java
@Entity
public class User {
    @Id private Long id;
    private String name;
    
    @OneToMany(mappedBy = "author")
    private List<Book> books;
}

@Entity
public class Book {
    @Id private Long id;
    private String title;
    private Integer year;
    
    @ManyToOne
    private User author;
}
```

### Syntaxe de Projection Collection

```json
{
  "projection": [
    "name",
    "books[size=5,sort=year:desc].title,year"
  ]
}
```

**Décomposition :**
- `books` : Nom de la collection
- `[size=5,sort=year:desc]` : Options de la collection
- `.title,year` : Champs à extraire de chaque élément

### Options Disponibles

| Option | Description | Défaut |
|--------|-------------|--------|
| `size=N` | Nombre max d'éléments | 10 |
| `page=P` | Page (0-indexé) | 0 |
| `sort=field:dir` | Tri (asc/desc) | - |

### Exemples

**Les 10 derniers livres, triés par année décroissante :**
```json
{
  "projection": ["name", "books[size=10,sort=year:desc].title,year"]
}
```

**Tri multi-colonnes :**
```json
{
  "projection": ["name", "books[sort=year:desc,title:asc].title,year"]
}
```

**Page 2 des livres (éléments 20-29) :**
```json
{
  "projection": ["name", "books[page=2,size=10].title"]
}
```

### Résultat

```json
{
  "content": [
    {
      "name": "Victor Hugo",
      "books": [
        { "title": "Les Misérables", "year": 1862 },
        { "title": "Notre-Dame de Paris", "year": 1831 }
      ]
    }
  ]
}
```

---

## Mapping JPA Avancé {#jpa-mapping}

### PredicateResolverMapping

Pour un contrôle total sur la génération des prédicats JPA :

```java
@Bean
public JpaFilterContext<ProductProperty> productContext(EntityManager em) {
    return new JpaFilterContext<>(
        ProductProperty.class,
        (root, prop) -> switch (prop) {
            // Mapping simple
            case NAME -> root.get("name");
            
            // Relation
            case CATEGORY_NAME -> root.get("category").get("name");
            
            // Calcul (attention aux performances)
            case TOTAL_STOCK -> root.get("warehouse1Stock")
                                    .add(root.get("warehouse2Stock"));
        }
    );
}
```

:::tip Opérateurs Personnalisés
Pour gérer des opérateurs personnalisés comme SOUNDEX, GEO_WITHIN, etc., voir [Opérateurs Personnalisés](#custom-operators) qui offre une approche plus flexible avec `CustomOperatorResolver`.
:::

### Stratégies d'Exécution

FilterQL JPA propose plusieurs stratégies d'exécution :

| Stratégie | Description | Usage |
|-----------|-------------|-------|
| `MultiQueryFetchStrategy` | Requête séparée pour le count et les données | **Recommandé** - Performance optimale |
| `FullEntityFetchStrategy` | Charge les entités complètes | Quand vous avez besoin des entités |
| `CountStrategy` | Compte uniquement, pas de données | Statistiques |

```java
// Utilisation explicite d'une stratégie
FilterQuery<User> query = FilterQueryFactory.of(context);
PaginatedResult<User> result = query.execute(
    request, 
    new MultiQueryFetchStrategy<>(em, User.class)
);
```

---

## Configuration Spring Avancée

### Personnaliser le Base Path

```java
@Projection(from = User.class)
@Exposure(value = "users", basePath = "/api/v2")
public class UserDTO {
    // ...
}
```

Génère : `POST /api/v2/users/search`

### Annotation @ExposedAs

Pour personnaliser l'exposition d'une propriété :

```java
@Projection(from = User.class)
@Exposure(value = "users", basePath = "/api")
public class UserDTO {

    @ExposedAs(value = "USERNAME", operators = {Op.EQ, Op.MATCHES})
    private String name;
    
    @ExposedAs(exposed = false)  // Non exposé à l'API (pas filtrable)
    private String internalId;
    
    @ExposedAs(value = "EMAIL", operators = {Op.EQ, Op.MATCHES})
    private String email;
    
    private Integer age;  // Sans @ExposedAs = non filtrable mais retourné dans la projection
}
```

## Champs Virtuels {#virtual-fields}

Les champs virtuels sont l'une des fonctionnalités les plus puissantes de FilterQL. Ils permettent de définir des **propriétés filtrables qui ne correspondent pas directement à des champs d'entité**, permettant une logique de requête complexe via une API simple.

### Pourquoi les Champs Virtuels ?

| Cas d'utilisation | Exemple |
|-------------------|---------|
| **Propriétés calculées** | Filtrer par `fullName` (combine prénom + nom) |
| **Alias sémantiques** | `isActive` au lieu de vérifications de statut complexes |
| **Encapsulation métier** | `hasAccess` avec vérification de rôles/permissions |
| **Recherches multi-champs** | Rechercher dans plusieurs colonnes simultanément |
| **Agrégations** | `orderCount > 10` basé sur des sous-requêtes |
| **Filtrage dynamique** | `withinMyOrg` basé sur le contexte de l'utilisateur courant |

### Syntaxe de Base

```java
import io.github.cyfko.filterql.core.spi.PredicateResolver;

@ExposedAs(value = "NOM_CHAMP", operators = {Op.MATCHES, Op.EQ})
public static PredicateResolver<Entity> nomMethode(String op, Object[] args) {
    return (root, query, cb) -> {
        // Logique de prédicat personnalisée utilisant l'API Criteria JPA
        return cb.equal(root.get("champ"), args[0]);
    };
}
```

**Exigences de la méthode :**
- **Type de retour :** `PredicateResolver<E>` où `E` est le type de l'entité
- **Paramètres :** `(String op, Object[] args)` — l'opérateur et les arguments du filtre
- **Visibilité :** `public static` (ou méthode d'instance pour les beans Spring)

---

### Champs Virtuels Statiques

Les méthodes statiques sont idéales pour la **logique de prédicat pure** qui ne nécessite pas de dépendances externes.

#### Exemple : Recherche par Nom Complet

```java
@Projection(from = Person.class)
@Exposure(value = "persons", basePath = "/api")
public class PersonDTO {

    @ExposedAs(value = "FIRST_NAME", operators = {Op.EQ, Op.MATCHES})
    private String firstName;

    @ExposedAs(value = "LAST_NAME", operators = {Op.EQ, Op.MATCHES})
    private String lastName;

    /**
     * Champ virtuel : recherche dans firstName OU lastName.
     */
    @ExposedAs(value = "FULL_NAME", operators = {Op.MATCHES})
    public static PredicateResolver<Person> fullNameMatches(String op, Object[] args) {
        return (root, query, cb) -> {
            if (args.length == 0) return cb.conjunction();
            String pattern = "%" + args[0] + "%";
            return cb.or(
                cb.like(root.get("firstName"), pattern),
                cb.like(root.get("lastName"), pattern)
            );
        };
    }
}
```

**Utilisation :**
```json
{
  "filters": {
    "name": { "ref": "FULL_NAME", "op": "MATCHES", "value": "john" }
  },
  "combineWith": "name"
}
```

Recherche "john" dans `firstName` OU `lastName`.

#### Exemple : Filtre Utilisateur Admin

```java
/**
 * Champ virtuel défini dans une classe de résolution dédiée.
 */
public class VirtualResolverConfig {

    @ExposedAs(value = "IS_ADMIN", operators = {Op.EQ})
    public static PredicateResolver<Person> isAdminUser(String op, Object[] args) {
        return (root, query, cb) -> {
            Boolean isAdmin = args.length > 0 ? (Boolean) args[0] : false;
            if (Boolean.TRUE.equals(isAdmin)) {
                return cb.equal(root.get("role"), "ADMIN");
            } else {
                return cb.notEqual(root.get("role"), "ADMIN");
            }
        };
    }
}

// Enregistrer comme provider dans le DTO
@Projection(
    entity = Person.class,
    providers = @Provider(VirtualResolverConfig.class)
)
public class PersonDTO { /* ... */ }
```

**Utilisation :**
```json
{
  "filters": {
    "admins": { "ref": "IS_ADMIN", "op": "EQ", "value": true }
  },
  "combineWith": "admins"
}
```

---

### Champs Virtuels d'Instance (Beans Spring)

Les méthodes d'instance sont puissantes pour le **filtrage contextuel** qui nécessite des services Spring, le contexte de sécurité, ou d'autres dépendances injectées.

#### Exemple : Filtrage Multi-Tenant

```java
@Component
public class UserTenancyService {

    @Autowired
    private SecurityContext securityContext;

    /**
     * Champ virtuel : filtre les utilisateurs de l'organisation de l'utilisateur courant.
     */
    @ExposedAs(value = "WITHIN_MY_ORG", operators = {Op.EQ})
    public PredicateResolver<Person> withinCurrentOrg(String op, Object[] args) {
        // Accéder à l'utilisateur courant depuis le contexte de sécurité
        String currentUserOrg = securityContext.getCurrentUser().getOrganization();
        
        return (root, query, cb) -> {
            Boolean withinOrg = args.length > 0 ? (Boolean) args[0] : true;
            if (Boolean.TRUE.equals(withinOrg)) {
                return cb.equal(root.get("organizationId"), currentUserOrg);
            } else {
                return cb.notEqual(root.get("organizationId"), currentUserOrg);
            }
        };
    }
}

// Enregistrer comme provider dans le DTO
@Projection(
    entity = Person.class,
    providers = @Provider(UserTenancyService.class)
)
public class PersonDTO { /* ... */ }
```

**Utilisation :**
```json
{
  "filters": {
    "myOrg": { "ref": "WITHIN_MY_ORG", "op": "EQ", "value": true }
  },
  "combineWith": "myOrg"
}
```

#### Exemple : Contrôle d'Accès Basé sur les Rôles

```java
@Component
public class AccessControlService {

    @Autowired
    private PermissionService permissions;

    /**
     * Filtre les ressources auxquelles l'utilisateur courant a accès.
     */
    @ExposedAs(value = "HAS_ACCESS", operators = {Op.EQ})
    public PredicateResolver<Document> hasAccess(String op, Object[] args) {
        List<Long> accessibleIds = permissions.getAccessibleResourceIds();
        
        return (root, query, cb) -> {
            Boolean hasAccess = args.length > 0 ? (Boolean) args[0] : true;
            if (Boolean.TRUE.equals(hasAccess)) {
                return root.get("id").in(accessibleIds);
            } else {
                return cb.not(root.get("id").in(accessibleIds));
            }
        };
    }
}
```

---

### Patterns Avancés

#### Utiliser l'Argument Opérateur

Le paramètre `op` vous donne un contrôle total sur le comportement spécifique à chaque opérateur :

```java
@ExposedAs(value = "COMPUTED_SCORE", operators = {Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE})
public static PredicateResolver<Product> computedScore(String op, Object[] args) {
    return (root, query, cb) -> {
        // Calculer le score comme : rating * 10 + reviewCount
        var score = cb.sum(
            cb.prod(root.get("rating"), 10),
            root.get("reviewCount")
        );
        
        Integer threshold = (Integer) args[0];
        
        return switch (op) {
            case "EQ" -> cb.equal(score, threshold);
            case "GT" -> cb.gt(score, threshold);
            case "LT" -> cb.lt(score, threshold);
            case "GTE" -> cb.ge(score, threshold);
            case "LTE" -> cb.le(score, threshold);
            default -> cb.conjunction();
        };
    };
}
```

#### Champs Virtuels Basés sur des Sous-Requêtes

```java
@ExposedAs(value = "ORDER_COUNT", operators = {Op.GT, Op.LT, Op.EQ})
public static PredicateResolver<Customer> orderCount(String op, Object[] args) {
    return (root, query, cb) -> {
        // Sous-requête pour compter les commandes
        var subquery = query.subquery(Long.class);
        var orderRoot = subquery.from(Order.class);
        subquery.select(cb.count(orderRoot))
               .where(cb.equal(orderRoot.get("customer"), root));
        
        Long count = ((Number) args[0]).longValue();
        
        return switch (op) {
            case "GT" -> cb.gt(subquery, count);
            case "LT" -> cb.lt(subquery, count);
            case "EQ" -> cb.equal(subquery, count);
            default -> cb.conjunction();
        };
    };
}
```

**Utilisation :**
```json
{
  "filters": {
    "bigCustomers": { "ref": "ORDER_COUNT", "op": "GT", "value": 10 }
  },
  "combineWith": "bigCustomers"
}
```

#### Combiner Champs Virtuels et Champs Réguliers

Les champs virtuels fonctionnent parfaitement avec les champs réguliers dans les expressions de filtre :

```json
{
  "filters": {
    "age": { "ref": "AGE", "op": "GT", "value": 18 },
    "name": { "ref": "FULL_NAME", "op": "MATCHES", "value": "John" },
    "myOrg": { "ref": "WITHIN_MY_ORG", "op": "EQ", "value": true }
  },
  "combineWith": "age & (name | myOrg)"
}
```

---

### Enregistrement des Providers

Les méthodes de champs virtuels peuvent être définies dans :

1. **La classe DTO elle-même** (pour une logique fortement couplée)
2. **Des classes de résolution dédiées** (pour une logique réutilisable)
3. **Des beans Spring** (pour une logique contextuelle)

Enregistrez-les avec `@Provider` :

```java
@Projection(
    entity = Person.class,
    providers = {
        @Provider(VirtualResolverConfig.class),  // Méthodes statiques
        @Provider(UserTenancyService.class)      // Bean Spring (méthodes d'instance)
    }
)
@Exposure(value = "persons", basePath = "/api")
public class PersonDTO {
    // ...
}
```

---

### Bonnes Pratiques

#### 1. Valider les Arguments

Toujours vérifier `args` avant d'y accéder :

```java
public static PredicateResolver<User> filter(String op, Object[] args) {
    return (root, query, cb) -> {
        if (args.length == 0 || args[0] == null) {
            return cb.conjunction();  // Aucun filtre appliqué
        }
        // Continuer avec la logique du filtre...
    };
}
```

#### 2. Utiliser des Noms Descriptifs

```java
// ❌ Obscur
@ExposedAs(value = "F1", operators = {Op.EQ})

// ✅ Auto-documenté
@ExposedAs(value = "IS_PREMIUM_CUSTOMER", operators = {Op.EQ})
```

#### 3. Limiter les Opérateurs

N'exposez que les opérateurs qui ont du sens pour le champ virtuel :

```java
// ❌ Trop permissif pour un champ booléen
@ExposedAs(value = "IS_ACTIVE", operators = {Op.EQ, Op.GT, Op.LT, Op.MATCHES})

// ✅ Approprié pour une sémantique booléenne
@ExposedAs(value = "IS_ACTIVE", operators = {Op.EQ})
```

#### 4. Documenter la Logique Complexe

```java
/**
 * Champ virtuel : Filtre les produits par score de popularité calculé.
 * 
 * Formule du score : (rating * 10) + (reviewCount * 2) + (salesCount / 100)
 * 
 * Opérateurs :
 * - GT/GTE : Produits avec score au-dessus du seuil
 * - LT/LTE : Produits avec score en-dessous du seuil
 * - EQ : Produits avec score exact (rarement utile)
 */
@ExposedAs(value = "POPULARITY_SCORE", operators = {Op.GT, Op.GTE, Op.LT, Op.LTE})
public static PredicateResolver<Product> popularityScore(String op, Object[] args) {
    // ...
}
```

---

## Opérateurs Personnalisés {#custom-operators}

FilterQL supporte des opérateurs personnalisés au-delà de l'ensemble standard (EQ, MATCHES, GT, etc.). L'interface `CustomOperatorResolver` fournit un moyen centralisé de gérer les opérateurs personnalisés ou de surcharger le comportement par défaut.

### Pourquoi des Opérateurs Personnalisés ?

| Cas d'utilisation | Exemple |
|-------------------|---------|
| **Fonctions base de données** | SOUNDEX, LEVENSHTEIN pour le matching flou |
| **Requêtes géospatiales** | GEO_WITHIN, GEO_DISTANCE pour le filtrage par localisation |
| **Recherche full-text** | FULL_TEXT utilisant les capacités spécifiques à la base |
| **Surcharge de comportement** | Logique MATCHES personnalisée pour des propriétés spécifiques |
| **Préoccupations transverses** | Filtrage par tenant, gestion du soft-delete |

### Interface CustomOperatorResolver

```java
@FunctionalInterface
public interface CustomOperatorResolver<P extends Enum<P> & PropertyReference> {
    
    /**
     * @param ref  la référence de propriété filtrée
     * @param op   le code de l'opérateur (ex: "SOUNDEX", "EQ")
     * @param args les arguments du filtre
     * @return PredicateResolver pour gérer cette opération, ou null pour utiliser le défaut
     */
    PredicateResolver<?> resolve(P ref, String op, Object[] args);
}
```

### Flux de Résolution

```
┌─────────────────────────────────────────────────────────────┐
│                    toCondition(ref, op)                      │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │ customOperatorResolver != null │
              │   && resolve() retourne non-null│
              └───────────────────────────────┘
                    │                    │
                   OUI                  NON
                    │                    │
                    ▼                    ▼
           ┌──────────────┐    ┌──────────────────────┐
           │ Utiliser le  │    │ Mécanisme par défaut │
           │   resolver   │    │ (path / Mapping)     │
           └──────────────┘    └──────────────────────┘
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
        
        // Gérer l'opérateur SOUNDEX
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

### Exemple : Plusieurs Opérateurs Personnalisés

```java
CustomOperatorResolver<UserProperty> multiResolver = (ref, op, args) -> {
    return switch (op) {
        case "SOUNDEX" -> handleSoundex(ref, args);
        case "LEVENSHTEIN" -> handleLevenshtein(ref, args);
        case "GEO_WITHIN" -> handleGeoWithin(ref, args);
        case "FULL_TEXT" -> handleFullText(ref, args);
        default -> null;  // Les opérateurs standards utilisent le mécanisme par défaut
    };
};

private static PredicateResolver<?> handleSoundex(UserProperty ref, Object[] args) {
    String field = getFieldPath(ref);
    return (root, query, cb) -> cb.equal(
        cb.function("SOUNDEX", String.class, root.get(field)),
        cb.function("SOUNDEX", String.class, cb.literal((String) args[0]))
    );
}

private static PredicateResolver<?> handleLevenshtein(UserProperty ref, Object[] args) {
    String field = getFieldPath(ref);
    String searchValue = (String) args[0];
    int maxDistance = args.length > 1 ? (Integer) args[1] : 2;
    
    return (root, query, cb) -> cb.le(
        cb.function("LEVENSHTEIN", Integer.class, 
            root.get(field), 
            cb.literal(searchValue)),
        maxDistance
    );
}
```

### Exemple : Surcharger un Opérateur Standard

Vous pouvez aussi surcharger des opérateurs standards pour des propriétés spécifiques :

```java
CustomOperatorResolver<UserProperty> emailOverride = (ref, op, args) -> {
    // Surcharger MATCHES pour EMAIL pour être insensible à la casse
    if (ref == UserProperty.EMAIL && "MATCHES".equals(op)) {
        return (root, query, cb) -> {
            String pattern = ((String) args[0]).toLowerCase();
            return cb.like(cb.lower(root.get("email")), pattern);
        };
    }
    
    // Surcharger EQ pour STATUS pour gérer les alias d'enum
    if (ref == UserProperty.STATUS && "EQ".equals(op)) {
        return (root, query, cb) -> {
            String status = normalizeStatus((String) args[0]);
            return cb.equal(root.get("status"), status);
        };
    }
    
    return null;  // Utiliser le défaut pour tout le reste
};
```

### Utilisation dans les Requêtes de Filtre

Une fois configurés, les opérateurs personnalisés fonctionnent naturellement dans les requêtes :

```json
{
  "filters": {
    "similar": { "ref": "FIRST_NAME", "op": "SOUNDEX", "value": "Jon" },
    "nearby": { "ref": "LOCATION", "op": "GEO_WITHIN", "value": [48.8566, 2.3522, 10] }
  },
  "combineWith": "similar & nearby"
}
```

### Bonnes Pratiques pour les Opérateurs Personnalisés

#### 1. Valider les Propriétés Supportées

```java
CustomOperatorResolver<UserProperty> resolver = (ref, op, args) -> {
    if ("SOUNDEX".equals(op)) {
        // N'autoriser SOUNDEX que sur les propriétés textuelles
        if (!Set.of(UserProperty.FIRST_NAME, UserProperty.LAST_NAME).contains(ref)) {
            throw new IllegalArgumentException(
                "Opérateur SOUNDEX non supporté pour la propriété : " + ref);
        }
        return handleSoundex(ref, args);
    }
    return null;
};
```

#### 2. Documenter les Opérateurs Personnalisés

```java
/**
 * Opérateurs personnalisés supportés :
 * 
 * SOUNDEX - Matching phonétique (FIRST_NAME, LAST_NAME uniquement)
 *   Usage : { "ref": "FIRST_NAME", "op": "SOUNDEX", "value": "Jon" }
 * 
 * GEO_WITHIN - Recherche par rayon géographique (LOCATION uniquement)
 *   Usage : { "ref": "LOCATION", "op": "GEO_WITHIN", "value": [lat, lon, rayonKm] }
 */
```

#### 3. Gérer les Arguments Manquants avec Élégance

```java
PredicateResolver<?> handleSoundex(UserProperty ref, Object[] args) {
    if (args.length == 0 || args[0] == null) {
        return (root, query, cb) -> cb.conjunction(); // Pas de filtre
    }
    // ...
}
```

---

## Bonnes Pratiques

N'autorisez que les opérateurs nécessaires :

```java
// ❌ Trop permissif
return OperatorUtils.ALL_OPS;

// ✅ Restrictif et intentionnel
return Set.of(Op.EQ, Op.MATCHES);
```

### 2. Validez les Types

Le type retourné par `getType()` est utilisé pour la validation et la conversion :

```java
// ❌ Retourne Object (pas de validation)
return Object.class;

// ✅ Type précis
return LocalDateTime.class;
```

### 3. Documentez les Propriétés

Utilisez des noms explicites dans votre enum :

```java
// ❌ Obscur
F1, F2, F3

// ✅ Auto-documenté
USER_EMAIL, CREATED_AT, IS_ACTIVE
```

### 4. Testez vos PropertyReference

```java
@Test
void allPropertiesShouldHaveTypes() {
    for (UserProperty prop : UserProperty.values()) {
        assertNotNull(prop.getType());
        assertFalse(prop.getSupportedOperators().isEmpty());
    }
}
```

---

## Référence Technique

Pour les détails d'implémentation complets :

| Sujet | Documentation |
|-------|---------------|
| API Core (interfaces, classes) | [→ Référence Core](./reference/core) |
| API JPA (contexte, stratégies) | [→ Référence JPA](./reference/jpa-adapter) |
| API Spring (annotations, services) | [→ Référence Spring](./reference/spring-adapter) |
| Spécification formelle du protocole | [→ Protocole](./protocol) |
