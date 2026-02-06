---
sidebar_position: 4
---

# Projection

La projection permet de sélectionner uniquement les champs nécessaires dans les résultats, optimisant ainsi les performances et le transfert de données.

---

## Syntaxe de Base

Les champs de projection sont spécifiés dans le `FilterRequest` :

```java
FilterRequest<UserPropertyRef> request = FilterRequest.<UserPropertyRef>builder()
    .filter("activeFilter", UserPropertyRef.STATUS, Op.EQ, UserStatus.ACTIVE)
    .combineWith("activeFilter")
    .projection("id", "username", "email")  // Champs projetés
    .build();
```

---

## Grammaire Formelle (EBNF)

```ebnf
(* Spécification de champ de projection *)
projection-field     = simple-field | nested-field | collection-field ;

(* Champ simple sans hiérarchie *)
simple-field         = field-name ;

(* Champ imbriqué avec notation point *)
nested-field         = field-path , "." , field-list ;

(* Champ de collection avec pagination/tri optionnel *)
collection-field     = field-path-with-options , "." , field-list ;

(* Chemin de champ (peut inclure options de collection) *)
field-path           = field-segment , { "." , field-segment } ;
field-segment-with-options = field-name , [ collection-options ] ;

(* Liste multi-champs (séparée par virgule après le dernier point) *)
field-list           = field-name , { "," , field-name } ;

(* Options de pagination/tri de collection *)
collection-options   = "[" , option-list , "]" ;
option-list          = option , { "," , option } ;
option               = size-option | page-option | sort-option ;

(* Options individuelles *)
size-option          = "size=" , positive-integer ;
page-option          = "page=" , non-negative-integer ;
sort-option          = "sort=" , sort-spec , { "," , sort-spec } ;
sort-spec            = field-name , ":" , sort-direction ;
sort-direction       = "asc" | "desc" | "ASC" | "DESC" ;
```

---

## Types de Projection

### Champs Simples

```java
.projection("id", "username", "email", "age")
```

### Champs Imbriqués

Pour les relations JPA (`@ManyToOne`, `@OneToOne`), utilisez la notation point :

```java
.projection(
    "id",
    "username",
    "address.city",        // Accède à user.address.city
    "address.country",     // Accède à user.address.country
    "department.name"      // Accède à user.department.name
)
```

### Syntaxe Multi-Champs Compacte

Pour plusieurs champs partageant le même préfixe :

```java
// Syntaxe compacte
.projection("id", "address.city,country,postalCode")

// Équivalent à :
.projection("id", "address.city", "address.country", "address.postalCode")
```

---

## Projection de Collections

FilterQL supporte la projection de collections (`@OneToMany`, `@ManyToMany`) avec pagination et tri inline.

### Options Disponibles

| Option | Description | Valeur par Défaut | Plage |
|--------|-------------|-------------------|-------|
| `size=N` | Taille de page | 10 | 1 à 10000 |
| `page=P` | Numéro de page (0-indexé) | 0 | 0+ |
| `sort=field:dir` | Tri par champ | - | asc/desc |

### Exemples

#### Pagination Simple

```java
// 10 premiers livres par auteur
.projection("id", "name", "books[size=10].title,year")
```

#### Avec Tri

```java
// 20 livres les plus récents
.projection("id", "name", "books[size=20,sort=year:desc].title,year")
```

#### Pagination et Tri Combinés

```java
// Page 2 (éléments 20-39), triés par année décroissante
.projection("id", "name", "books[size=20,page=1,sort=year:desc].title,author,year")
```

#### Tri Multi-Colonnes

```java
// Trier par année desc, puis par titre asc
.projection("id", "books[sort=year:desc,title:asc].title,year")
```

#### Pagination Hiérarchique

```java
// 10 auteurs par entité, 5 livres par auteur
.projection(
    "id",
    "name",
    "authors[size=10].name,books[size=5,sort=year:desc].title,year"
)
```

---

## Entités et DTOs

### Définition d'Entité

```java
@Entity
public class Author {
    @Id
    private Long id;
    private String name;
    
    @OneToMany(mappedBy = "author")
    private List<Book> books;
    
    @ManyToMany
    @JoinTable(name = "author_awards")
    private Set<Award> awards;
}

@Entity
public class Book {
    @Id
    private Long id;
    private String title;
    private Integer year;
    
    @ManyToOne
    private Author author;
}
```

### Définition de DTO avec @Projection

:::note Dépendance Externe
L'annotation `@Projection` provient de [projection-spec](https://github.com/cyfko/projection-spec), implémentée par [projection-metamodel-processor](https://github.com/cyfko/jpa-metamodel-processor).
:::

```java
import io.github.cyfko.projection.Projection;
import io.github.cyfko.projection.Projected;

@Projection(from = Author.class)
public class AuthorDTO {
    private Long id;
    private String name;
    
    @Projected(from = "books")
    private List<BookSummaryDTO> books;
    
    @Projected(from = "awards")
    private Set<AwardDTO> awards;
}

@Projection(from = Book.class)
public class BookSummaryDTO {
    private Long id;
    private String title;
    private Integer year;
}
```

---

## Exécution

### Sans Pagination de Collection

```java
FilterRequest<AuthorPropertyRef> request = FilterRequest.<AuthorPropertyRef>builder()
    .filter("nameFilter", AuthorPropertyRef.NAME, Op.MATCHES, "%smith%")
    .combineWith("nameFilter")
    .projection("id", "name", "books.id,title,year", "awards.name")
    .pagination(new Pagination(0, 20, List.of(new SortBy("name", "ASC"))))
    .build();

MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(AuthorDTO.class);
QueryExecutor<List<Map<String, Object>>> executor = filterQuery.toExecutor(request);
List<Map<String, Object>> results = executor.executeWith(em, strategy);

// Résultat : Tous les livres et awards pour chaque auteur (sans limite)
```

### Avec Pagination de Collection

```java
FilterRequest<AuthorPropertyRef> request = FilterRequest.<AuthorPropertyRef>builder()
    .filter("nameFilter", AuthorPropertyRef.NAME, Op.MATCHES, "%smith%")
    .combineWith("nameFilter")
    .projection(
        "id",
        "name",
        "books[size=5,sort=year:desc].title,year"  // 5 derniers livres
    )
    .pagination(new Pagination(0, 20, null))
    .build();

MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(AuthorDTO.class);
List<Map<String, Object>> results = executor.executeWith(em, strategy);
```

### Résultat JSON

```json
{
  "data": [
    {
      "id": 1,
      "name": "John Smith",
      "books": [
        {"title": "Latest Book", "year": 2024},
        {"title": "Previous Work", "year": 2023},
        {"title": "Classic Novel", "year": 2022},
        {"title": "Early Writing", "year": 2021},
        {"title": "First Book", "year": 2020}
      ]
    }
  ],
  "pagination": {
    "currentPage": 0,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

## Règles de Validation

### Options Cohérentes

Les références multiples à la même collection doivent utiliser des options identiques :

```java
// ❌ INVALIDE : options conflictuelles pour 'books'
.projection(
    "books[size=10].title",
    "books[size=20].author"  // ERREUR : size différent
)

// ✅ VALIDE : options identiques
.projection(
    "books[size=10,sort=year:desc].title",
    "books[size=10,sort=year:desc].author"
)

// ✅ VALIDE : utiliser la syntaxe multi-champs
.projection(
    "books[size=10,sort=year:desc].title,author"
)
```

### Limites de Taille

```java
// ❌ INVALIDE : size > 10000
.projection("books[size=50000].title")  // ERREUR

// ❌ INVALIDE : size < 1
.projection("books[size=0].title")  // ERREUR
```

---

## Considérations de Performance

| Aspect | Recommandation |
|--------|----------------|
| **Fetch par défaut** | Sans options, les collections sont récupérées entièrement |
| **Batch fetching** | `MultiQueryFetchStrategy` utilise le batch fetching pour éviter N+1 |
| **Mémoire** | La pagination réduit l'empreinte mémoire pour les grandes collections |
| **Index** | Les champs de tri DOIVENT être indexés pour des performances optimales |

---

## Prochaines Étapes

- [Agrégations sur Collections](../advanced-guide#reducers) - Calculer des totaux, moyennes, comptages sur des collections imbriquées
- [Opérateurs Personnalisés](custom-operators) - Créer des opérateurs métier
- [Référence JPA Adapter](../reference/jpa-adapter) - Stratégies d'exécution détaillées
