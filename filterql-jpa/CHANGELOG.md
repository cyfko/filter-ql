# Changelog — filterql-adapter-jpa

All notable changes to this project will be documented in this file.

---

## [2.0.0] — 2026-02-24 — ⚠️ BREAKING CHANGE

This release brings `filterql-adapter-jpa` into full conformance with the **core 4.0.0 redesign**. It is not compatible with any prior version of either this adapter or the core module.

### What changed and why

The 1.x line of this adapter was tightly bound to an older core that mixed framework-specific concerns directly into its interfaces. Now that `core` is fully agnostic (see [core 4.0.0 changelog](https://github.com/cyfko/filterql)), this adapter has been rewritten to act as a **pure bridge** between the agnostic core contracts and the JPA/persistence layer — with no leakage in either direction.

### Breaking Changes

- **Conformance to core 4.0.0** — all adapter interfaces have been realigned to implement the new core contracts. Any class extending or implementing types from `filterql-adapter-jpa` 1.x must be rewritten.
- **Minimum required core version is now 4.0.0** — this adapter will not work with core 3.x or below.
- **Removed framework-coupled abstractions** — types that previously duplicated or overrode core logic specific to JPA have been removed. The adapter now strictly handles JPA-specific concerns and delegates everything else to core.
- **Package restructuring** — several adapter-specific classes have been reorganized to better reflect the adapter/core boundary. Refer to the updated Javadoc for new locations.

### Migration

If you are upgrading from `filterql-adapter-jpa` 1.x:

1. Upgrade `core` to **4.0.0+** first (see core migration guide).
2. Replace your `filterql-adapter-jpa` dependency with version **2.0.0**.
3. Reimplement any custom classes that extended adapter types, using the new interface contracts.
4. Remove any direct imports of types that have been deleted or relocated (the compiler will guide you).

---

> `filterql-adapter-jpa` 1.x is considered legacy and will no longer receive patches. Upgrading to 2.0.0 alongside core 4.0.x is strongly recommended.
