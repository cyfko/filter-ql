---
sidebar_position: 3
---

# Référence Spring Adapter

Documentation de référence complète pour le module `filterql-spring` (version 4.0.0).

---

## Coordonnées Maven

```xml
<!-- Option 1 : Starter (recommandé) -->
<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>filterql-spring-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Option 2 : Module seul -->
<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>filterql-spring</artifactId>
    <version>4.0.0</version>
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
io.github.cyfko.filterql.spring
├── Exposure                    # Annotation d'exposition REST
├── ExposedAs                   # Annotation de personnalisation de champ
├── processor/
│   ├── ExposureAnnotationProcessor  # Processeur d'annotations
│   ├── PropertyRefEnumGenerator     # Générateur d'enum
│   ├── FilterContextGenerator       # Générateur de configuration
│   └── FilterControllerGenerator    # Générateur de controller
├── service/
│   ├── FilterQlService              # Interface de service
│   └── impl/
│       └── FilterQlServiceImpl      # Implémentation
├── pagination/
│   ├── PaginatedData                # Wrapper de résultats
│   ├── PaginationInfo               # Métadonnées de pagination
│   └── ResultMapper                 # Interface de transformation
├── support/
│   ├── FilterContextRegistry        # Registre de contextes
│   └── SpringProviderResolver       # Résolveur IoC Spring
└── autoconfigure/
    └── FilterQlAutoConfiguration    # Auto-configuration Spring Boot
```

---

## Annotations

### @Exposure

Marque une classe de projection pour la génération de controller REST.

```java
package io.github.cyfko.filterql.spring;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Exposure {
    
    /**
     * Nom de la ressource REST (kebab-case).
     * Défaut : nom de classe en kebab-case.
     */
    String value() default "";
    
    /**
     * Préfixe de chemin URI optionnel.
     */
    String basePath() default "";
    
    /**
     * Référence de méthode pour les annotations d'endpoint.
     */
    MethodReference annotationsFrom() default @MethodReference();
}
```

#### Utilisation

```java
import io.github.cyfko.projection.Projection;
import io.github.cyfko.filterql.spring.Exposure;

@Projection(from = User.class)
@Exposure(value = "users", basePath = "/api/v1")
public class UserDTO {
    private Long id;
    private String username;
    private String email;
}
```

Génère un endpoint : `POST /api/v1/users/search`

#### Annotations d'Endpoint

Pour appliquer des annotations (sécurité, cache, etc.) aux endpoints générés :

```java
@Projection(from = User.class)
@Exposure(value = "users")
public class UserDTO {
    
    // Méthode template pour les annotations
    @PreAuthorize("hasRole('ADMIN')")
    @Cacheable("userCache")
    private static void exposureEndpoint() {}
}
```

Ou avec une classe de templates partagée :

```java
// Templates partagés
public class SecurityTemplates {
    
    @PreAuthorize("hasRole('ADMIN')")
    @Cacheable("adminCache")
    @RateLimiter(name = "admin")
    public static void adminEndpoint() {}
    
    @PreAuthorize("hasRole('USER')")
    @RateLimiter(name = "user")
    public static void userEndpoint() {}
}

// Utilisation
@Projection(from = User.class)
@Exposure(
    value = "users",
    annotationsFrom = @MethodReference(
        type = SecurityTemplates.class,
        method = "adminEndpoint"
    )
)
public class UserDTO { }
```

---

### @ExposedAs

Personnalise l'exposition d'un champ dans l'enum PropertyRef généré.

```java
package io.github.cyfko.filterql.spring;

@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
public @interface ExposedAs {
    
    /**
     * Nom symbolique dans l'enum généré.
     */
    String value();
    
    /**
     * Opérateurs supportés.
     */
    Op[] operators() default {};
    
    /**
     * Si le champ est exposé au filtrage.
     */
    boolean exposed() default true;
}
```

#### Utilisation

```java
@Projection(from = User.class)
@Exposure(value = "users")
public class UserDTO {
    
    private Long id;
    
    @ExposedAs(value = "USERNAME", operators = {Op.EQ, Op.MATCHES, Op.IN})
    private String username;
    
    @ExposedAs(value = "USER_EMAIL", operators = {Op.EQ, Op.MATCHES})
    private String email;
    
    @ExposedAs(exposed = false)  // Exclure du filtrage
    private String internalField;
}
```

Génère :

```java
public enum UserDTO_ implements PropertyReference {
    USERNAME,
    USER_EMAIL;
    // internalField non inclus
}
```

#### Champs Virtuels

Les champs virtuels permettent une logique de prédicat personnalisée sans champ d'entité correspondant :

```java
import io.github.cyfko.filterql.core.spi.PredicateResolver;

@ExposedAs(value = "FULL_NAME", operators = {Op.MATCHES})
public static PredicateResolver<User> fullNameSearch(String op, Object[] args) {
    return (root, query, cb) -> {
        String pattern = "%" + args[0] + "%";
        return cb.or(
            cb.like(root.get("firstName"), pattern),
            cb.like(root.get("lastName"), pattern)
        );
    };
}
```

**Exigences de la méthode :**
- Doit être `public static` (ou méthode d'instance si gérée par Spring)
- Type de retour : `PredicateResolver<E>` où `E` est le type de l'entité
- Paramètres : `(String op, Object[] args)` — l'opérateur et les arguments du filtre

Les champs virtuels peuvent aussi être non-statiques (méthodes d'instance) quand ils ont besoin d'accéder aux beans Spring.

---

## Services

### FilterQlService

Interface principale pour l'exécution de filtres.

```java
package io.github.cyfko.filterql.spring.service;

public interface FilterQlService {
    
    /**
     * Recherche avec résultat en Map.
     * 
     * @param refClass classe de l'enum PropertyReference
     * @param filterRequest requête de filtre
     * @return données paginées
     */
    <P extends Enum<P> & PropertyReference> 
    PaginatedData<Map<String, Object>> search(
        Class<P> refClass, 
        FilterRequest<P> filterRequest
    );
    
    /**
     * Recherche avec mapping personnalisé.
     * 
     * @param projectionClass classe DTO cible
     * @param filterRequest requête de filtre
     * @param resultMapper mapper de résultats
     * @return données paginées typées
     */
    <R, P extends Enum<P> & PropertyReference> 
    PaginatedData<R> search(
        Class<R> projectionClass, 
        FilterRequest<P> filterRequest, 
        ResultMapper<R> resultMapper
    );
}
```

#### Injection et Utilisation

```java
import io.github.cyfko.filterql.spring.service.FilterQlService;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    
    private final FilterQlService filterQlService;
    
    public UserService(FilterQlService filterQlService) {
        this.filterQlService = filterQlService;
    }
    
    public PaginatedData<Map<String, Object>> searchUsers(
            FilterRequest<UserDTO_> request) {
        return filterQlService.search(UserDTO_.class, request);
    }
}
```

---

### FilterQlServiceImpl

Implémentation par défaut de `FilterQlService`.

```java
package io.github.cyfko.filterql.spring.service.impl;

@Service
public class FilterQlServiceImpl implements FilterQlService {
    
    @PersistenceContext
    private EntityManager em;
    
    private final FilterContextRegistry contextRegistry;
    private final InstanceResolver instanceResolver;
    
    // Implémentation...
}
```

---

## Pagination

### PaginatedData

Wrapper immutable pour les résultats paginés.

```java
package io.github.cyfko.filterql.spring.pagination;

public record PaginatedData<T>(
    List<T> data,
    PaginationInfo pagination
) {
    
    /**
     * Constructeur avec copie défensive.
     */
    public PaginatedData(List<T> data, PaginationInfo pagination) {
        this.data = List.copyOf(data);
        this.pagination = pagination;
    }
    
    /**
     * Constructeur depuis Spring Data Page.
     */
    public PaginatedData(Page<T> page) {
        this(page.getContent(), PaginationInfo.from(page));
    }
    
    /**
     * Transforme les données avec un mapper.
     * 
     * @param mapper fonction de transformation
     * @return nouvelles données paginées
     */
    public <R> PaginatedData<R> map(Function<T, R> mapper) {
        return new PaginatedData<>(
            data.stream().map(mapper).collect(Collectors.toList()), 
            pagination
        );
    }
}
```

---

### PaginationInfo

Métadonnées de pagination.

```java
package io.github.cyfko.filterql.spring.pagination;

public record PaginationInfo(
    int currentPage,
    int pageSize,
    long totalElements
) {
    
    /**
     * Calcule le nombre total de pages.
     */
    public int totalPages() {
        return (int) Math.ceil((double) totalElements / pageSize);
    }
    
    /**
     * Crée depuis une Page Spring Data.
     */
    public static PaginationInfo from(Page<?> page) {
        return new PaginationInfo(
            page.getNumber(),
            page.getSize(),
            page.getTotalElements()
        );
    }
}
```

---

### ResultMapper

Interface de transformation de résultats.

```java
package io.github.cyfko.filterql.spring.pagination;

@FunctionalInterface
public interface ResultMapper<R> {
    
    /**
     * Transforme un résultat Map en type cible.
     * 
     * @param row données brutes
     * @return objet transformé
     */
    R map(Map<String, Object> row);
}
```

#### Exemple d'Utilisation

```java
ResultMapper<UserDTO> mapper = row -> new UserDTO(
    (Long) row.get("id"),
    (String) row.get("username"),
    (String) row.get("email")
);

PaginatedData<UserDTO> result = filterQlService.search(
    UserDTO.class, 
    request, 
    mapper
);
```

---

## Registre

### FilterContextRegistry

Registre central de tous les beans `JpaFilterContext`.

```java
package io.github.cyfko.filterql.spring.support;

@Component
public class FilterContextRegistry {
    
    /**
     * Construit le registre depuis les contextes injectés.
     */
    public FilterContextRegistry(List<JpaFilterContext<?>> contexts);
    
    /**
     * Récupère le contexte pour une classe enum.
     * 
     * @param enumClass classe de l'enum PropertyReference
     * @return contexte correspondant
     * @throws IllegalArgumentException si non trouvé
     */
    public <P extends Enum<P> & PropertyReference> 
    JpaFilterContext<?> getContext(Class<P> enumClass);
}
```

---

### SpringProviderResolver

Résolveur de beans Spring pour les champs `@Computed`.

```java
package io.github.cyfko.filterql.spring.service.impl;

@Component
public class SpringProviderResolver implements InstanceResolver {
    
    private final ApplicationContext applicationContext;
    
    public SpringProviderResolver(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    @Override
    public <T> T resolve(Class<T> providerClass) {
        return applicationContext.getBean(providerClass);
    }
}
```

---

## Processeur d'Annotations

### ExposureAnnotationProcessor

Processeur de compilation qui génère le code FilterQL.

```java
package io.github.cyfko.filterql.spring.processor;

@SupportedAnnotationTypes("io.github.cyfko.projection.Projection")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class ExposureAnnotationProcessor extends AbstractProcessor {
    
    /**
     * Traite les classes annotées @Projection.
     * Génère :
     * - Enum PropertyRef ({ClassName}_)
     * - Configuration FilterContext (FilterQlContextConfig)
     * - Controller REST (FilterQlController) si @Exposure présent
     */
    @Override
    public boolean process(
        Set<? extends TypeElement> annotations, 
        RoundEnvironment roundEnv
    );
}
```

### Artefacts Générés

Pour une classe `UserDTO` annotée avec `@Projection` et `@Exposure` :

#### 1. Enum PropertyRef

```java
// Fichier généré: UserDTO_.java
public enum UserDTO_ implements PropertyReference {
    USERNAME,
    EMAIL,
    AGE;
    
    @Override
    public Class<?> getType() {
        var pm = ProjectionRegistry.getMetadataFor(UserDTO.class);
        return switch(this) {
            case USERNAME -> pm.getDirectMapping("username", true).get().dtoFieldType();
            case EMAIL -> pm.getDirectMapping("email", true).get().dtoFieldType();
            case AGE -> pm.getDirectMapping("age", true).get().dtoFieldType();
        };
    }
    
    @Override
    public Set<Op> getSupportedOperators() {
        return switch(this) {
            case USERNAME -> Set.of(Op.EQ, Op.MATCHES, Op.IN);
            case EMAIL -> Set.of(Op.EQ, Op.MATCHES);
            case AGE -> Set.of(Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE);
        };
    }
    
    @Override
    public Class<?> getEntityType() {
        return User.class;
    }
}
```

#### 2. Configuration FilterContext

```java
// Fichier généré: FilterQlContextConfig.java
@Configuration
public class FilterQlContextConfig {
    
    @Bean
    public JpaFilterContext<?> userDTOContext(InstanceResolver instanceResolver) {
        return new JpaFilterContext<>(UserDTO_.class, (ref) -> switch (ref) {
            case USERNAME -> "username";
            case EMAIL -> "email";
            case AGE -> "age";
        });
    }
}
```

#### 3. Controller REST

```java
// Fichier généré: FilterQlController.java
@RestController
public class FilterQlController {
    
    @Autowired
    private FilterQlService filterQlService;
    
    @PostMapping("/api/v1/users/search")
    public ResponseEntity<PaginatedData<Map<String, Object>>> searchUserDTO(
        @RequestBody @Validated FilterRequest<UserDTO_> request
    ) {
        return ResponseEntity.ok(filterQlService.search(UserDTO_.class, request));
    }
}
```

---

## Auto-Configuration

### FilterQlAutoConfiguration

Configuration automatique Spring Boot.

```java
package io.github.cyfko.filterql.spring.autoconfigure;

@Configuration
@ConditionalOnClass(JpaFilterContext.class)
@EnableConfigurationProperties(FilterQlProperties.class)
public class FilterQlAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public FilterContextRegistry filterContextRegistry(
            List<JpaFilterContext<?>> contexts) {
        return new FilterContextRegistry(contexts);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public InstanceResolver instanceResolver(ApplicationContext ctx) {
        return new SpringProviderResolver(ctx);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public FilterQlService filterQlService(
            EntityManager em,
            FilterContextRegistry registry,
            InstanceResolver resolver) {
        return new FilterQlServiceImpl(em, registry, resolver);
    }
}
```

---

## Exemple Complet

### DTO avec Projection et Exposure

```java
import io.github.cyfko.projection.Projection;
import io.github.cyfko.projection.Projected;
import io.github.cyfko.projection.Computed;
import io.github.cyfko.filterql.spring.Exposure;
import io.github.cyfko.filterql.spring.ExposedAs;
import org.springframework.security.access.prepost.PreAuthorize;

@Projection(from = User.class)
@Exposure(value = "users", basePath = "/api/v1")
public class UserDTO {
    
    private Long id;
    
    @ExposedAs(value = "USERNAME", operators = {Op.EQ, Op.MATCHES, Op.IN})
    private String username;
    
    @Projected(from = "email")
    @ExposedAs(value = "EMAIL", operators = {Op.EQ, Op.MATCHES})
    private String userEmail;
    
    @Projected(from = "address.city.name")
    @ExposedAs(value = "CITY", operators = {Op.EQ, Op.IN})
    private String cityName;
    
    @Computed(provider = AgeCalculator.class, method = "calculateAge")
    @ExposedAs(exposed = false)  // Non filtrable
    private Integer age;
    
    @Projected(from = "orders")
    private List<OrderSummaryDTO> orders;
    
    // Template d'annotations pour l'endpoint généré
    @PreAuthorize("hasRole('USER')")
    private static void exposureEndpoint() {}
}
```

### Requête HTTP

```bash
curl -X POST http://localhost:8080/api/v1/users/search \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "filters": {
      "nameFilter": {"ref": "USERNAME", "op": "MATCHES", "value": "%john%"},
      "cityFilter": {"ref": "CITY", "op": "IN", "value": ["Paris", "Lyon"]}
    },
    "combineWith": "nameFilter & cityFilter",
    "projection": ["id", "username", "userEmail", "cityName", "orders[size=5].id,total"],
    "pagination": {"page": 1, "size": 20}
  }'
```

### Réponse

```json
{
  "data": [
    {
      "id": 1,
      "username": "john.doe",
      "userEmail": "john@example.com",
      "cityName": "Paris",
      "orders": [
        {"id": 101, "total": 150.00},
        {"id": 98, "total": 75.50}
      ]
    }
  ],
  "pagination": {
    "currentPage": 1,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

## Prochaines Étapes

- [Spécification du Protocole](../protocol) - Format de message formel
- [Guide Projection](../guides/projection) - Syntaxe de projection avancée
