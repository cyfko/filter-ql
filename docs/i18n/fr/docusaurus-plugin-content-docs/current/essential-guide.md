---
sidebar_position: 3
---

# Guide Essentiel

Tout ce dont vous avez besoin pour 80% des cas d'utilisation. Du plus simple au plus utile.

---

## 1. Les Opérateurs

Un opérateur définit **comment** comparer une propriété à une valeur.

### Opérateurs de Comparaison

| Opérateur | Signification | Exemple JSON | Équivalent SQL |
|-----------|---------------|--------------|----------------|
| `EQ` | Égal à | `{"ref": "STATUS", "op": "EQ", "value": "active"}` | `status = 'active'` |
| `NE` | Différent de | `{"ref": "STATUS", "op": "NE", "value": "deleted"}` | `status != 'deleted'` |
| `GT` | Supérieur à | `{"ref": "AGE", "op": "GT", "value": 18}` | `age > 18` |
| `GTE` | Supérieur ou égal | `{"ref": "AGE", "op": "GTE", "value": 18}` | `age >= 18` |
| `LT` | Inférieur à | `{"ref": "PRICE", "op": "LT", "value": 100}` | `price < 100` |
| `LTE` | Inférieur ou égal | `{"ref": "PRICE", "op": "LTE", "value": 100}` | `price <= 100` |

### Opérateurs de Pattern

| Opérateur | Signification | Exemple JSON | Équivalent SQL |
|-----------|---------------|--------------|----------------|
| `MATCHES` | Contient (insensible à la casse) | `{"ref": "NAME", "op": "MATCHES", "value": "john"}` | `LOWER(name) LIKE '%john%'` |
| `NOT_MATCHES` | Ne contient pas | `{"ref": "NAME", "op": "NOT_MATCHES", "value": "test"}` | `LOWER(name) NOT LIKE '%test%'` |

### Opérateurs de Liste

| Opérateur | Signification | Exemple JSON | Équivalent SQL |
|-----------|---------------|--------------|----------------|
| `IN` | Dans la liste | `{"ref": "STATUS", "op": "IN", "value": ["active", "pending"]}` | `status IN ('active', 'pending')` |
| `NOT_IN` | Pas dans la liste | `{"ref": "STATUS", "op": "NOT_IN", "value": ["deleted"]}` | `status NOT IN ('deleted')` |

### Opérateurs de Plage

| Opérateur | Signification | Exemple JSON | Équivalent SQL |
|-----------|---------------|--------------|----------------|
| `RANGE` | Entre min et max (inclusif) | `{"ref": "AGE", "op": "RANGE", "value": [18, 65]}` | `age BETWEEN 18 AND 65` |
| `NOT_RANGE` | Hors de la plage | `{"ref": "AGE", "op": "NOT_RANGE", "value": [18, 65]}` | `age NOT BETWEEN 18 AND 65` |

### Opérateurs de Nullité

| Opérateur | Signification | Exemple JSON | Équivalent SQL |
|-----------|---------------|--------------|----------------|
| `IS_NULL` | Est null | `{"ref": "EMAIL", "op": "IS_NULL", "value": null}` | `email IS NULL` |
| `NOT_NULL` | N'est pas null | `{"ref": "EMAIL", "op": "NOT_NULL", "value": null}` | `email IS NOT NULL` |

---

## 2. Combiner les Filtres

Le champ `combineWith` contient une expression booléenne qui combine vos filtres.

### Opérateurs Booléens

| Symbole | Signification | Priorité |
|---------|---------------|----------|
| `!` | NON (négation) | Plus haute |
| `&` | ET | Moyenne |
| `\|` | OU | Plus basse |
| `( )` | Groupement | Override la priorité |

### Exemples Progressifs

**Un seul filtre :**
```json
{
  "filters": {
    "f1": { "ref": "STATUS", "op": "EQ", "value": "active" }
  },
  "combineWith": "f1"
}
```

**Deux filtres avec ET :**
```json
{
  "filters": {
    "isActive": { "ref": "STATUS", "op": "EQ", "value": "active" },
    "isAdult": { "ref": "AGE", "op": "GTE", "value": 18 }
  },
  "combineWith": "isActive & isAdult"
}
```
*Résultat : utilisateurs actifs ET adultes*

**Avec OU :**
```json
{
  "filters": {
    "isActive": { "ref": "STATUS", "op": "EQ", "value": "active" },
    "isPending": { "ref": "STATUS", "op": "EQ", "value": "pending" }
  },
  "combineWith": "isActive | isPending"
}
```
*Résultat : utilisateurs actifs OU en attente*

**Avec négation :**
```json
{
  "filters": {
    "isDeleted": { "ref": "STATUS", "op": "EQ", "value": "deleted" }
  },
  "combineWith": "!isDeleted"
}
```
*Résultat : utilisateurs NON supprimés*

**Expression complexe :**
```json
{
  "filters": {
    "hasEmail": { "ref": "EMAIL", "op": "NOT_NULL", "value": null },
    "isAdult": { "ref": "AGE", "op": "GTE", "value": 18 },
    "isVip": { "ref": "STATUS", "op": "EQ", "value": "vip" },
    "isAdmin": { "ref": "ROLE", "op": "EQ", "value": "admin" }
  },
  "combineWith": "hasEmail & isAdult & (isVip | isAdmin)"
}
```
*Résultat : a un email ET est adulte ET (est VIP OU admin)*

### Raccourcis Pratiques

Pour combiner **tous** les filtres :
- `"AND"` : Combine tout avec ET
- `"OR"` : Combine tout avec OU

```json
{
  "filters": {
    "f1": { "ref": "STATUS", "op": "EQ", "value": "active" },
    "f2": { "ref": "AGE", "op": "GTE", "value": 18 },
    "f3": { "ref": "EMAIL", "op": "NOT_NULL", "value": null }
  },
  "combineWith": "AND"
}
```
*Équivalent à : `"f1 & f2 & f3"`*

---

## 3. Projection

Choisir exactement quels champs retourner. Moins de données = réponse plus rapide.

### Syntaxe de Base

```json
{
  "filters": { ... },
  "combineWith": "...",
  "projection": ["name", "email", "age"]
}
```

**Réponse :**
```json
{
  "content": [
    { "name": "John", "email": "john@example.com", "age": 30 },
    { "name": "Jane", "email": "jane@example.com", "age": 25 }
  ]
}
```

### Champs Imbriqués

Pour accéder aux propriétés d'objets liés :

```json
{
  "projection": ["name", "address.city", "address.country"]
}
```

**Syntaxe compacte (équivalent) :**
```json
{
  "projection": ["name", "address.city,country"]
}
```

### Sans Projection

Si vous n'envoyez pas de `projection`, tous les champs de l'entité sont retournés.

---

## 4. Pagination

Contrôler la quantité de résultats et naviguer dans les pages.

### Syntaxe

```json
{
  "filters": { ... },
  "combineWith": "...",
  "pagination": {
    "page": 0,
    "size": 20,
    "sort": [
      { "property": "name", "direction": "ASC" },
      { "property": "createdAt", "direction": "DESC" }
    ]
  }
}
```

| Champ | Description | Défaut |
|-------|-------------|--------|
| `page` | Numéro de page (commence à 0) | 0 |
| `size` | Nombre d'éléments par page | 10 |
| `sort` | Critères de tri | - |

### Réponse Paginée

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 156,
  "totalPages": 8
}
```

---

## 5. Configuration des Propriétés Filtrables

FilterQL offre deux approches pour déclarer les propriétés filtrables.

### Approche Spring (Recommandée)

Avec Spring Boot, utilisez les annotations directement sur votre DTO :

```java
@Projection(from = User.class)
@Exposure(value = "users", basePath = "/api")
public class UserDTO {

    private Long id;

    @ExposedAs(value = "NAME", operators = {Op.EQ, Op.NE, Op.MATCHES, Op.IN})
    private String name;

    @ExposedAs(value = "EMAIL", operators = {Op.EQ, Op.NE, Op.MATCHES})
    private String email;

    @ExposedAs(value = "AGE", operators = {Op.EQ, Op.GT, Op.GTE, Op.LT, Op.LTE, Op.RANGE})
    private Integer age;

    @ExposedAs(value = "STATUS", operators = {Op.EQ, Op.NE, Op.IN})
    private String status;

    @ExposedAs(value = "CREATED_AT", operators = {Op.GT, Op.GTE, Op.LT, Op.LTE, Op.RANGE})
    private LocalDateTime createdAt;

    // Getters...
}
```

**Ce que cela fait :**
- `@Projection(from = User.class)` : Lie ce DTO à l'entité JPA `User`
- `@Exposure(value = "users", basePath = "/api")` : Génère `POST /api/users/search`
- `@ExposedAs(value = "NAME", operators = {...})` : Déclare ce champ comme filtrable

**Ce qui est généré automatiquement à la compilation :**
- L'enum `UserDTO_ implements PropertyReference` (nom du DTO + underscore `_`)
- Le controller REST avec l'endpoint `/api/users/search`
- Toute la logique de validation, parsing DSL et mapping JPA

:::info Génération Automatique
Vous n'avez **jamais** besoin de créer manuellement l'enum `PropertyReference` avec Spring. Le processeur d'annotations le génère pour vous à partir des `@ExposedAs`.
:::

### Approche Core (Programmatique)

Sans Spring, ou pour un contrôle total, implémentez `PropertyReference` manuellement :

```java
public enum UserProperty implements PropertyReference {
    NAME, EMAIL, AGE, STATUS, CREATED_AT;

    @Override
    public Class<?> getType() {
        return switch (this) {
            case NAME, EMAIL, STATUS -> String.class;
            case AGE -> Integer.class;
            case CREATED_AT -> LocalDateTime.class;
        };
    }

    @Override
    public Set<Op> getSupportedOperators() {
        return switch (this) {
            case NAME, EMAIL -> Set.of(Op.EQ, Op.NE, Op.MATCHES, Op.IN);
            case AGE -> Set.of(Op.EQ, Op.GT, Op.GTE, Op.LT, Op.LTE, Op.RANGE);
            case STATUS -> Set.of(Op.EQ, Op.NE, Op.IN);
            case CREATED_AT -> Set.of(Op.GT, Op.GTE, Op.LT, Op.LTE, Op.RANGE);
        };
    }

    @Override
    public Class<?> getEntityType() {
        return User.class;
    }
}
```

Cette approche nécessite ensuite de créer manuellement l'endpoint et d'utiliser `FilterQueryFactory`.

### Convention de Nommage (Approche Core)

Par défaut, FilterQL convertit le nom de l'enum en nom de propriété JPA :

| Enum | Propriété JPA |
|------|---------------|
| `NAME` | `name` |
| `EMAIL` | `email` |
| `CREATED_AT` | `createdAt` |
| `USER_STATUS` | `userStatus` |

La conversion suit la règle : `SCREAMING_SNAKE_CASE` → `camelCase`.

---

## 6. Requête Complète

Voici une requête utilisant toutes les fonctionnalités :

```json
{
  "filters": {
    "nameFilter": { "ref": "NAME", "op": "MATCHES", "value": "john" },
    "ageFilter": { "ref": "AGE", "op": "RANGE", "value": [25, 45] },
    "statusFilter": { "ref": "STATUS", "op": "IN", "value": ["active", "vip"] },
    "hasEmail": { "ref": "EMAIL", "op": "NOT_NULL", "value": null }
  },
  "combineWith": "(nameFilter | ageFilter) & statusFilter & hasEmail",
  "projection": ["name", "email", "status"],
  "pagination": {
    "page": 0,
    "size": 25,
    "sort": [{ "property": "name", "direction": "ASC" }]
  }
}
```

**Ce que ça fait :**
1. Cherche les utilisateurs dont (le nom contient "john" OU l'âge est entre 25 et 45)
2. ET qui ont un statut "active" ou "vip"
3. ET qui ont un email non-null
4. Retourne seulement name, email, status
5. Trie par nom, 25 résultats par page, première page

---

## Et Ensuite ?

Vous connaissez maintenant 80% de FilterQL. Pour les cas avancés :

| Besoin | Guide |
|--------|-------|
| Filtrer sur des relations (ex: `user.address.city`) | [→ Guide Avancé](./advanced-guide#relations) |
| Créer des opérateurs personnalisés | [→ Guide Avancé](./advanced-guide#custom-operators) |
| Mapping JPA avancé | [→ Guide Avancé](./advanced-guide#jpa-mapping) |
| Collections avec pagination inline | [→ Guide Avancé](./advanced-guide#collections) |
