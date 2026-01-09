# Changelog

All notable changes to this project are documented in this file.

## [Unreleased]
### Added
- Advanced configuration strategies: `NullValuePolicy`, `EnumMatchMode`, `StringCaseStrategy` for flexible value interpretation and matching.
- Collection semantics for singleton operators (`EQ`, `NE`, `GT`, `IN`):
  - Accept collections for positive singleton ops (OR semantics).
  - Accept collections for negative singleton ops (AND semantics).
  - Accept scalars for `IN` operator.
- Integration tests for new strategies and collection semantics.
- Debug logging in validation layer to confirm runtime value types and collection handling.
- Documentation updates: expanded guides in `README.md`, `ARCHITECTURE.md`, and `docs/configuration.md`.
- Cross-links between documentation files for easier navigation.

### Changed
- Validation logic in `PropertyReference` patched to support new collection semantics and scalar handling for operators.
- Refactored enum coercion and case-insensitive matching.

### Fixed
- Validation layer now correctly accepts collections and scalars as required by operator semantics.
- All tests pass (core and spring adapter modules).

### Notes
- Multi-module build requires installing core before running adapter tests.
- See `docs/configuration.md` for strategy details and usage examples.
