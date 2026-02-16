---
sidebar_position: 2
---

# Historique des Versions

Historique complet des versions et changements du projet FilterQL.

---

## Versions Actuelles

| Module | Version |
|--------|---------|
| `filterql-core` | 4.0.0 |
| `filterql-adapter-jpa` | 2.0.0 |
| `filterql-spring` | 4.0.0 |
| `filterql-spring-processor` | 4.0.0 |

---

## Changelog Complet

Le changelog détaillé est maintenu dans le fichier [CHANGELOG.md](https://github.com/cyfko/filter-ql/blob/main/CHANGELOG.md) du dépôt GitHub.

### Consulter le Changelog

🔗 **Changelog officiel** : [https://github.com/cyfko/filter-ql/blob/main/CHANGELOG.md](https://github.com/cyfko/filter-ql/blob/main/CHANGELOG.md)

---

## Notes de Version par Module

### filterql-spring

Le module Spring maintient également son propre changelog :

🔗 **Changelog Spring** : [https://github.com/cyfko/filter-ql/blob/main/adapters/java/filterql-spring/CHANGELOG.md](https://github.com/cyfko/filter-ql/blob/main/adapters/java/filterql-spring/CHANGELOG.md)

---

## Releases GitHub

Toutes les versions publiées sont disponibles sur la page des releases :

🔗 **Releases** : [https://github.com/cyfko/filter-ql/releases](https://github.com/cyfko/filter-ql/releases)

---

## Maven Central

Les artefacts sont publiés sur Maven Central :

- [filterql-core](https://central.sonatype.com/artifact/io.github.cyfko/filterql-core)
- [filterql-adapter-jpa](https://central.sonatype.com/artifact/io.github.cyfko/filterql-adapter-jpa)
- [filterql-spring](https://central.sonatype.com/artifact/io.github.cyfko/filterql-spring)
- [filterql-spring-processor](https://central.sonatype.com/artifact/io.github.cyfko/filterql-spring-processor)

---

## Politique de Versionnement

FilterQL suit le [Semantic Versioning](https://semver.org/) :

- **MAJOR** : Changements incompatibles de l'API
- **MINOR** : Nouvelles fonctionnalités rétro-compatibles
- **PATCH** : Corrections de bugs rétro-compatibles

### Compatibilité Java

| Version | Java Minimum |
|---------|--------------|
| Core 4.x | Java 21+ |
| JPA Adapter 2.x | Java 21+ |
| Spring 4.x | Java 17+ |

### Compatibilité Spring Boot

| Version | Spring Boot |
|---------|-------------|
| Spring 4.x | 3.3.5+ |
