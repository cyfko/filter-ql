---
sidebar_position: 1
---

# Contributing

Thank you for your interest in contributing to FilterQL! This guide explains how to participate in the project.

---

## Source Code

The project is hosted on GitHub:

🔗 **Repository**: [https://github.com/cyfko/filter-ql](https://github.com/cyfko/filter-ql)

---

## How to Contribute

### 1. Report a Bug

1. Check that the bug hasn't already been reported in the [Issues](https://github.com/cyfko/filter-ql/issues)
2. Create a new issue with:
   - Clear description of the problem
   - Steps to reproduce
   - Expected vs observed behavior
   - FilterQL version used
   - Environment (Java, Spring Boot, database)

### 2. Propose a Feature

1. Open an issue describing the desired feature
2. Explain the concrete use case
3. Discuss the approach before implementing

### 3. Submit Code

1. **Fork** the repository
2. **Create a branch** for your change:
   ```bash
   git checkout -b feature/my-feature
   ```
3. **Make your changes** following project conventions
4. **Add tests** for new code
5. **Verify** that all tests pass:
   ```bash
   ./mvnw clean verify
   ```
6. **Commit** with a descriptive message:
   ```bash
   git commit -m "feat: short description of the feature"
   ```
7. **Push** your branch:
   ```bash
   git push origin feature/my-feature
   ```
8. **Open a Pull Request** to the `main` branch

---

## Code Conventions

### Java Style

- **Indentation**: 4 spaces (no tabs)
- **Line length**: 120 characters maximum
- **Javadoc**: Required for public classes and methods
- **Naming**: 
  - Classes: `PascalCase`
  - Methods/Variables: `camelCase`
  - Constants: `SCREAMING_SNAKE_CASE`

### Commits

Use the [Conventional Commits](https://www.conventionalcommits.org/) format:

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

**Common types:**

| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `refactor` | Restructuring without functional change |
| `test` | Adding or fixing tests |
| `chore` | Maintenance (dependencies, configuration) |

---

## Project Structure

```
filter-ql/
├── core/java/               # filterql-core module
│   └── src/main/java/       # Main API
├── adapters/java/
│   ├── filterql-jpa/        # JPA adapter
│   ├── filterql-spring/     # Spring integration
│   └── filterql-spring-starter/  # Auto-configuration
├── integration-test/        # Integration tests
└── docs/                    # Docusaurus documentation
```

---

## Tests

### Running Tests

```bash
# Unit tests for a module
cd core/java
./mvnw test

# Tests with coverage
./mvnw verify

# Integration tests
cd integration-test
./mvnw verify
```

### Code Coverage

JaCoCo coverage reports are generated in `target/site/jacoco/`.

---

## Documentation

The documentation uses [Docusaurus](https://docusaurus.io/). To contribute:

```bash
cd docs
npm install
npm run start    # Local development
npm run build    # Production build
```

---

## License

By contributing, you agree that your contributions will be submitted under the [MIT License](https://github.com/cyfko/filter-ql/blob/main/LICENSE).

---

## Contact

- **GitHub Issues**: [github.com/cyfko/filter-ql/issues](https://github.com/cyfko/filter-ql/issues)
- **Discussions**: [github.com/cyfko/filter-ql/discussions](https://github.com/cyfko/filter-ql/discussions)

---

Thank you for contributing to FilterQL! 🎉
