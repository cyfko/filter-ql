---
sidebar_position: 1
---

# Contribution

Merci de votre intérêt pour contribuer à FilterQL ! Ce guide explique comment participer au projet.

---

## Code Source

Le projet est hébergé sur GitHub :

🔗 **Dépôt** : [https://github.com/cyfko/filter-ql](https://github.com/cyfko/filter-ql)

---

## Comment Contribuer

### 1. Signaler un Bug

1. Vérifiez que le bug n'est pas déjà signalé dans les [Issues](https://github.com/cyfko/filter-ql/issues)
2. Créez une nouvelle issue avec :
   - Description claire du problème
   - Étapes de reproduction
   - Comportement attendu vs observé
   - Version de FilterQL utilisée
   - Environnement (Java, Spring Boot, base de données)

### 2. Proposer une Fonctionnalité

1. Ouvrez une issue décrivant la fonctionnalité souhaitée
2. Expliquez le cas d'usage concret
3. Discutez de l'approche avant d'implémenter

### 3. Soumettre du Code

1. **Fork** le dépôt
2. **Créez une branche** pour votre modification :
   ```bash
   git checkout -b feature/ma-fonctionnalite
   ```
3. **Effectuez vos modifications** en suivant les conventions du projet
4. **Ajoutez des tests** pour le nouveau code
5. **Vérifiez** que tous les tests passent :
   ```bash
   ./mvnw clean verify
   ```
6. **Committez** avec un message descriptif :
   ```bash
   git commit -m "feat: description courte de la fonctionnalité"
   ```
7. **Poussez** votre branche :
   ```bash
   git push origin feature/ma-fonctionnalite
   ```
8. **Ouvrez une Pull Request** vers la branche `main`

---

## Conventions de Code

### Style Java

- **Indentation** : 4 espaces (pas de tabulations)
- **Longueur de ligne** : 120 caractères maximum
- **Javadoc** : Obligatoire pour les classes et méthodes publiques
- **Nommage** : 
  - Classes : `PascalCase`
  - Méthodes/Variables : `camelCase`
  - Constantes : `SCREAMING_SNAKE_CASE`

### Commits

Utilisez le format [Conventional Commits](https://www.conventionalcommits.org/) :

```
<type>(<scope>): <description>

[body optionnel]

[footer optionnel]
```

**Types courants :**

| Type | Description |
|------|-------------|
| `feat` | Nouvelle fonctionnalité |
| `fix` | Correction de bug |
| `docs` | Documentation uniquement |
| `refactor` | Restructuration sans changement fonctionnel |
| `test` | Ajout ou correction de tests |
| `chore` | Maintenance (dépendances, configuration) |

---

## Structure du Projet

```
filter-ql/
├── core/java/               # Module filterql-core
│   └── src/main/java/       # API principale
├── adapters/java/
│   ├── filterql-jpa/        # Adaptateur JPA
│   ├── filterql-spring/     # Intégration Spring
│   └── filterql-spring-starter/  # Auto-configuration
├── integration-test/        # Tests d'intégration
└── docs/                    # Documentation Docusaurus
```

---

## Tests

### Exécuter les Tests

```bash
# Tests unitaires d'un module
cd core/java
./mvnw test

# Tests avec couverture
./mvnw verify

# Tests d'intégration
cd integration-test
./mvnw verify
```

### Couverture de Code

Les rapports de couverture JaCoCo sont générés dans `target/site/jacoco/`.

---

## Documentation

La documentation utilise [Docusaurus](https://docusaurus.io/). Pour contribuer :

```bash
cd docs
npm install
npm run start    # Développement local
npm run build    # Build de production
```

---

## Licence

En contribuant, vous acceptez que vos contributions soient soumises sous la [Licence MIT](https://github.com/cyfko/filter-ql/blob/main/LICENSE).

---

## Contact

- **Issues GitHub** : [github.com/cyfko/filter-ql/issues](https://github.com/cyfko/filter-ql/issues)
- **Discussions** : [github.com/cyfko/filter-ql/discussions](https://github.com/cyfko/filter-ql/discussions)

---

Merci de contribuer à FilterQL ! 🎉
