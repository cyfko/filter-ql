# Changelog

All notable changes to this project will be documented in this file.

---

## [4.0.2] — 2026-02-24

### Fixes
- Minor bug fixes and stability improvements introduced in 4.0.0.

---

## [4.0.1] — 2026-02-24

### Fixes
- Post-refactor adjustments following the 4.0.0 breaking redesign.

---

## [4.0.0] — 2026-02-24 — ⚠️ BREAKING CHANGE

This release is a **major redesign** of the library. Most core interfaces and the overall architecture have been reworked from the ground up. **Upgrading from any 3.x version requires a full migration.**

### What changed and why

Prior versions tightly coupled the `core` module to specific framework assumptions (JPA, Spring, etc.), making it difficult to use in alternative stacks. Starting from 4.0.0, the `core` module is **fully agnostic** — it carries zero dependency on any persistence provider, framework, or runtime.

All framework-specific concerns have been pushed to dedicated adapter modules (e.g. `filterql-adapter-jpa`), which remain responsible for bridging the agnostic core with concrete implementations.

### Breaking Changes

- **Core interfaces redesigned** — the majority of principal interfaces have been redefined to remove all framework-specific assumptions. Implementations from 3.x are not compatible and must be rewritten against the new contracts.
- **Core module is now fully agnostic** — no JPA, no Spring, no runtime dependencies in `core`. Any such dependency now lives exclusively in adapter modules.
- **Package restructuring** — several types have been moved or renamed as part of the agnosticity effort. Check the updated Javadoc for the new locations.
- **Adapter modules are now required** — if you were using `core` directly with JPA, you must now explicitly depend on the appropriate adapter (e.g. `filterql-adapter-jpa`).

### Migration

There is no automatic migration path from 3.x. The recommended approach is:

1. Keep your 3.x dependency in place while migrating incrementally.
2. Add the new `core` + the relevant adapter module.
3. Reimplement your custom interfaces against the new contracts defined in `core`.
4. Remove the 3.x dependency once migration is complete.

Refer to the updated README and Javadoc for the new interface contracts.

---

> All versions prior to 4.0.0 are considered legacy. No further patches will be issued for the 3.x line.
