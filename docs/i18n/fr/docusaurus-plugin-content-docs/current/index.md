---
slug: /
sidebar_position: 1
---

# FilterQL

## Le Problème Que Vous Avez

Vous développez une API REST avec Spring Boot. Vous avez une entité `User` :

```java
@Entity
public class User {
    private Long id;
    private String name;
    private String email;
    private Integer age;
    private String status;
    private List<Book> books;
}
```

Votre client (application web, mobile) veut :
- Chercher les utilisateurs dont le nom contient "john"
- Filtrer ceux qui ont plus de 25 ans
- Ne récupérer que `name` et `email` (pas tout l'objet)
- Paginer les résultats (page 1, 20 par page)

### Ce Que Vous Faites Aujourd'hui

**Option 1 : Endpoints multiples**
```java
@GetMapping("/users/by-name/{name}")
@GetMapping("/users/by-age-greater-than/{age}")
@GetMapping("/users/by-name-and-age/{name}/{age}")
// ... et 20 autres combinaisons
```

**Option 2 : Paramètres query string**
```java
@GetMapping("/users")
public List<User> search(
    @RequestParam(required = false) String name,
    @RequestParam(required = false) Integer minAge,
    @RequestParam(required = false) Integer maxAge,
    // ... 15 autres paramètres
) {
    // 200 lignes de if/else pour construire la requête
}
```

**Les deux sont un cauchemar de maintenance.**

---

## La Solution FilterQL

Un seul endpoint. Une requête JSON. C'est le client qui décide quoi filtrer.

### Requête HTTP

```http
POST /api/users/search
Content-Type: application/json

{
  "filters": {
    "byName": { "ref": "NAME", "op": "MATCHES", "value": "john" },
    "isAdult": { "ref": "AGE", "op": "GTE", "value": 25 }
  },
  "combineWith": "byName & isAdult",
  "projection": ["name", "email"],
  "pagination": { "page": 0, "size": 20 }
}
```

### Réponse

```json
{
  "data": [
    { "name": "John Doe", "email": "john.doe@example.com" },
    { "name": "Johnny Smith", "email": "johnny@example.com" }
  ],
  "pagination": {
    "currentPage": 0,
    "pageSize": 20,
    "totalElements": 2,
    "totalPages": 1
  }
}
```

### Ce Que Vous Avez Écrit Côté Serveur

```java
@Projection(from = User.class)
@Exposure(value = "users", basePath = "/api")
public interface UserDTO {

    @ExposedAs(value = "NAME", operators = {Op.EQ, Op.MATCHES})
    String getName();

    @ExposedAs(value = "EMAIL", operators = {Op.EQ, Op.MATCHES})
    String getEmail();

    @ExposedAs(value = "AGE", operators = {Op.EQ, Op.GT, Op.GTE, Op.LT, Op.LTE})
    Integer getAge();

    @ExposedAs(value = "STATUS", operators = {Op.EQ, Op.IN})
    String getStatus();
}
```

**C'est tout.** L'endpoint `POST /api/users/search` est généré automatiquement.

---

## Comment Ça Marche

```
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│  Client Web  │      │   FilterQL   │      │   Base de    │
│  envoie JSON │─────▶│   traduit    │─────▶│   Données    │
└──────────────┘      └──────────────┘      └──────────────┘
                             │
                             ▼
                      ┌──────────────┐
                      │   Réponse    │
                      │   JSON       │
                      └──────────────┘
```

1. **Le client** envoie ce qu'il veut (filtres, champs, pagination)
2. **FilterQL** valide, parse, traduit en requête JPA
3. **La base de données** exécute
4. **Le client** reçoit exactement ce qu'il a demandé

---

## Prêt ?

| Votre Objectif | Commencez Ici |
|----------------|---------------|
| Voir un exemple complet fonctionnel | [→ Hello World](./hello-world) |
| Comprendre les bases rapidement | [→ Guide Essentiel](./essential-guide) |
| Cas avancés et personnalisation | [→ Guide Avancé](./advanced-guide) |
| Documentation technique complète | [→ Référence API](./reference/core) |

---

## Installation Rapide

**Maven (Spring Boot)** :

```xml
<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>filterql-spring</artifactId>
    <version>4.0.0</version>
</dependency>
```

Configurez le processeur d'annotations dans le plugin du compilateur :

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.github.cyfko</groupId>
                        <artifactId>filterql-spring-processor</artifactId>
                        <version>1.0.0</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

**Gradle** :

```kotlin
implementation("io.github.cyfko:filterql-spring:4.0.0")
annotationProcessor("io.github.cyfko:filterql-spring-processor:1.0.0")
```

C'est tout ce qu'il faut pour commencer. [→ Passons au Hello World](./hello-world)
