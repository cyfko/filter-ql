---
sidebar_position: 2
---

# Hello World

De zéro à une API de recherche fonctionnelle en 5 minutes.

---

## Ce Que Nous Allons Construire

Une API REST qui permet de chercher des utilisateurs avec :
- Filtrage dynamique (par nom, email, âge...)
- Combinaison de critères (AND, OR, NOT)
- Projection (choisir les champs retournés)
- Pagination

**Résultat final :** Un endpoint `POST /api/users/search` qui accepte des requêtes JSON.

---

## Étape 1 : Dépendances

Ajoutez FilterQL à votre projet Spring Boot.

**Maven** (`pom.xml`) :

```xml
<dependencies>
    <!-- FilterQL Spring Starter -->
    <dependency>
        <groupId>io.github.cyfko</groupId>
        <artifactId>filterql-spring-starter</artifactId>
        <version>4.0.0</version>
    </dependency>
    
    <!-- Vos dépendances Spring existantes -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

**Gradle** (`build.gradle.kts`) :

```kotlin
dependencies {
    implementation("io.github.cyfko:filterql-spring-starter:4.0.0")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
}
```

---

## Étape 2 : Votre Entité JPA

Vous avez probablement déjà une entité. Utilisons un exemple simple :

```java
package com.example.demo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    
    private String email;
    
    private Integer age;
    
    private String status;
    
    // Getters et setters...
}
```

---

## Étape 3 : Créer le DTO avec Exposition

C'est **LA** partie FilterQL. Vous déclarez un DTO qui définit :
- Quels champs sont retournés
- Quels champs sont filtrables et avec quels opérateurs

```java
package com.example.demo.dto;

import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.spring.ExposedAs;
import io.github.cyfko.filterql.spring.Exposure;
import io.github.cyfko.projection.Projection;
import com.example.demo.entity.User;

@Projection(entity = User.class)
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

    // Getters...
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public Integer getAge() { return age; }
    public String getStatus() { return status; }
}
```

**Ce que cela signifie :**
- `@Projection(entity = User.class)` : Ce DTO représente l'entité `User`
- `@Exposure(value = "users", basePath = "/api")` : Génère `POST /api/users/search`
- `@ExposedAs(value = "NAME", operators = {...})` : Ce champ est filtrable avec ces opérateurs

:::tip Mapping Personnalisé
Si le nom du champ DTO diffère de l'entité, utilisez `@Projected` :
```java
@Projected(from = "orderNumber")  // Entité: orderNumber → DTO: number
private String number;
```
Voir le [Guide Avancé](./advanced-guide#projections) pour plus de détails.
:::

---

## Étape 4 : Lancez l'Application

```bash
./mvnw spring-boot:run
```

**C'est terminé.** L'endpoint existe maintenant.

---

## Étape 5 : Testez

### Requête Simple (un seul filtre)

```bash
curl -X POST http://localhost:8080/api/users/search \
  -H "Content-Type: application/json" \
  -d '{
    "filters": {
      "f1": { "ref": "NAME", "op": "MATCHES", "value": "john" }
    },
    "combineWith": "f1"
  }'
```

**Résultat :** Tous les utilisateurs dont le nom contient "john".

---

### Requête Combinée (plusieurs filtres)

```bash
curl -X POST http://localhost:8080/api/users/search \
  -H "Content-Type: application/json" \
  -d '{
    "filters": {
      "byName": { "ref": "NAME", "op": "MATCHES", "value": "john" },
      "isAdult": { "ref": "AGE", "op": "GTE", "value": 18 },
      "isActive": { "ref": "STATUS", "op": "EQ", "value": "active" }
    },
    "combineWith": "byName & isAdult & isActive"
  }'
```

**Résultat :** Utilisateurs dont le nom contient "john", qui ont 18 ans ou plus, et qui sont actifs.

---

### Avec Projection et Pagination

```bash
curl -X POST http://localhost:8080/api/users/search \
  -H "Content-Type: application/json" \
  -d '{
    "filters": {
      "adults": { "ref": "AGE", "op": "GTE", "value": 18 }
    },
    "combineWith": "adults",
    "projection": ["name", "email"],
    "pagination": { "page": 0, "size": 10 }
  }'
```

**Résultat :**

```json
{
  "content": [
    { "name": "Alice Martin", "email": "alice@example.com" },
    { "name": "Bob Wilson", "email": "bob@example.com" }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 42,
  "totalPages": 5
}
```

---

## Récapitulatif

| Ce que vous avez fait | Temps |
|-----------------------|-------|
| Ajouté une dépendance Maven | 30 sec |
| Créé un DTO annoté (~20 lignes) | 2 min |
| Lancé l'application | 30 sec |

**Ce qui est généré automatiquement à la compilation :**
- L'enum `UserDTO_ implements PropertyReference` (nom du DTO + underscore `_`)
- Le controller REST avec l'endpoint `/api/users/search`
- La logique de validation des opérateurs
- Le mapping JPA

| Ce que vous avez obtenu |
|-------------------------|
| Endpoint de recherche REST |
| Filtrage dynamique sur 4 propriétés |
| 7 opérateurs différents |
| Combinaison booléenne arbitraire |
| Projection des champs |
| Pagination intégrée |

---

## Et Ensuite ?

Vous maîtrisez maintenant le flux de base. Pour aller plus loin :

| Objectif | Guide |
|----------|-------|
| Comprendre tous les opérateurs, la syntaxe DSL, la projection | [→ Guide Essentiel](./essential-guide) |
| Relations, opérateurs custom, mapping JPA avancé | [→ Guide Avancé](./advanced-guide) |
| Référence technique complète | [→ API Reference](./reference/core) |
