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

@Projection(entity = Order.class)
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
@Projection(entity = User.class, providers = @Provider(UserUtils.class))
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
@Projection(entity = User.class)
@Exposure(value = "users", basePath = "/api")
public class UserDTO {

    @ExposedAs(value = "NAME", operators = {Op.EQ, Op.MATCHES})
    private String name;

    private AddressDTO address;  // DTO imbriqué
}

@Projection(entity = Address.class)
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

## Opérateurs Personnalisés {#custom-operators}

FilterQL permet d'ajouter vos propres opérateurs via SPI.

### Cas d'Usage

- Recherche phonétique (Soundex, Metaphone)
- Recherche full-text
- Opérateurs géographiques (distance, within)
- Opérateurs JSON

### Implémentation

**1. Créer le provider :**

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
            if (!(definition.value() instanceof String searchValue) || searchValue.isBlank()) {
                throw new IllegalArgumentException("SOUNDEX requiert une valeur String non vide");
            }
            
            String fieldName = definition.ref().name().toLowerCase();
            return cb.equal(
                cb.function("SOUNDEX", String.class, root.get(fieldName)),
                cb.function("SOUNDEX", String.class, cb.literal(searchValue))
            );
        };
    }
}
```

**2. Enregistrer le provider :**

```java
@Configuration
public class FilterConfig {
    
    @PostConstruct
    public void registerOperators() {
        OperatorProviderRegistry.register(new SoundexOperatorProvider());
    }
}
```

**3. Autoriser l'opérateur dans PropertyReference :**

```java
@Override
public Set<Op> getSupportedOperators() {
    return switch (this) {
        case NAME -> Set.of(Op.EQ, Op.MATCHES, Op.CUSTOM); // CUSTOM autorise les opérateurs enregistrés
        // ...
    };
}
```

**4. Utiliser dans une requête :**

```json
{
  "filters": {
    "phonetic": { "ref": "NAME", "op": "SOUNDEX", "value": "Smith" }
  },
  "combineWith": "phonetic"
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
@Projection(entity = User.class)
@Exposure(value = "users", basePath = "/api/v2")
public class UserDTO {
    // ...
}
```

Génère : `POST /api/v2/users/search`

### Annotation @ExposedAs

Pour personnaliser l'exposition d'une propriété :

```java
@Projection(entity = User.class)
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

### Champ Virtuel avec Prédicat Personnalisé

Vous pouvez créer des champs "virtuels" qui exécutent une logique personnalisée :

```java
@Projection(entity = Person.class)
@Exposure(value = "persons", basePath = "/api")
public class PersonDTO {

    @ExposedAs(value = "FIRST_NAME", operators = {Op.EQ, Op.MATCHES})
    private String firstName;

    @ExposedAs(value = "LAST_NAME", operators = {Op.EQ, Op.MATCHES})
    private String lastName;

    /**
     * Champ virtuel : cherche dans firstName ET lastName.
     * La méthode statique retourne un PredicateResolverMapping.
     */
    @ExposedAs(value = "FULL_NAME", operators = {Op.MATCHES})
    public static PredicateResolverMapping<Person> fullNameMatches() {
        return (root, query, cb, params) -> {
            if (params.length == 0) return cb.conjunction();
            String pattern = "%" + params[0] + "%";
            return cb.or(
                cb.like(root.get("firstName"), pattern),
                cb.like(root.get("lastName"), pattern)
            );
        };
    }
}
```

**Usage :**
```json
{
  "filters": {
    "name": { "ref": "FULL_NAME", "op": "MATCHES", "value": "john" }
  },
  "combineWith": "name"
}
```

Cherche "john" dans `firstName` OU `lastName`.

---

## Bonnes Pratiques

### 1. Limitez les Opérateurs

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
