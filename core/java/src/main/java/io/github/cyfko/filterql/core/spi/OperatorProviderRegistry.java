package io.github.cyfko.filterql.core.spi;

import io.github.cyfko.filterql.core.validation.Op;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry and management facility for filter operator providers,
 * including both standard and custom implementations.
 * <p>
 * This singleton-style registry maintains a thread-safe mapping of operator codes to
 * {@link CustomOperatorProvider} instances. It serves as the primary lookup and registration
 * point for implementing filter operators beyond the built-in {@link Op} enum.
 * </p>
 * <p>
 * Operator providers can be dynamically registered or unregistered, facilitating extensibility
 * of filtering capabilities at runtime or configuration time.
 * </p>
 *
 * <p><strong>Concurrency:</strong> Internal storage uses a {@link ConcurrentHashMap}
 * which ensures safe concurrent access and modifications.</p>
 *
 * <p><strong>Case Normalization:</strong> Operator codes are normalized to upper case
 * before registration, lookup, and unregistration to allow case-insensitive usage.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Register a provider for custom operators
 * OperatorProviderRegistry.register(myCustomOperatorProvider);
 *
 * // Lookup a provider case-insensitively
 * Optional<CustomOperatorProvider> provider = OperatorProviderRegistry.getProvider("my_op");
 * provider.ifPresent(p -> {
 *     PredicateResolver<?> resolver = p.toResolver(filterDefinition);
 *     // Use the resolver to build predicates for queries
 * });
 *
 * // Unregister a provider instance when no longer needed
 * OperatorProviderRegistry.unregister(myCustomOperatorProvider);
 *
 * // Unregister multiple operator codes at once (case-insensitive codes)
 * OperatorProviderRegistry.unregister(Set.of("my_op", "another_op"));
 *
 * // Clear all registrations (useful for testing or full reset)
 * OperatorProviderRegistry.unregisterAll();
 * }</pre>
 *
 * <p><strong>Note:</strong> There is no direct unregister method for a single operator code String.
 * Use the set-based method with a singleton set to unregister a single operator code.</p>
 *
 * @author Frank
 * @since 4.0.0
 */
public final class OperatorProviderRegistry {
    private static final Map<String, CustomOperatorProvider> OPERATORS = new ConcurrentHashMap<>();

    private OperatorProviderRegistry() {
        // Prevent external instantiation
    }

    /**
     * Registers a new {@link CustomOperatorProvider} and its supported operators.
     * <p>
     * Throws {@link IllegalArgumentException} if any provided operator code is already registered
     * to a different provider.
     * </p>
     *
     * @param provider the custom operator provider to register
     * @throws IllegalArgumentException if operator code is already assigned
     */
    public static void register(CustomOperatorProvider provider) {
        Set<String> newOperators = Objects.requireNonNull(provider).supportedOperators();
        for (String operator : newOperators) {
            String op = operator.toUpperCase(Locale.ROOT);
            var previous = OPERATORS.putIfAbsent(op, provider);
            if (previous != null) {
                throw new IllegalArgumentException("Operator [" + op + "] is already registered.");
            }
        }
    }

    /**
     * Unregisters all operator mappings associated with the given provider.
     * <p>
     * After this call, the provider will no longer be associated with any operator in the registry.
     * </p>
     *
     * @param provider the provider to unregister
     */
    public static void unregister(CustomOperatorProvider provider) {
        OPERATORS.entrySet().removeIf(entry -> entry.getValue() == provider);
    }

    /**
     * Unregisters the provider associated with all of these specific operator codes.
     * <p>
     * After this call, these operator codes will no longer be associated with any provider.
     * </p>
     *
     * @param operators operator codes to unregister, case-insensitively
     */
    public static void unregister(Set<String> operators) {
        for (String op : operators) {
            OPERATORS.remove(op.trim().toUpperCase(Locale.ROOT));
        }
    }

    /**
     * Retrieves the registered {@link CustomOperatorProvider} for the given operator code.
     * <p>
     * Lookup is case-insensitive due to normalization to upper-case keys.
     * Returns an empty {@link Optional} if no provider is registered for the code.
     * </p>
     *
     * @param operator the operator code to look up
     * @return an {@link Optional} containing the provider if registered, or empty if none
     */
    public static Optional<CustomOperatorProvider> getProvider(String operator) {
        if (operator == null || operator.isBlank()) return Optional.empty();
        return Optional.ofNullable(OPERATORS.get(operator.trim().toUpperCase(Locale.ROOT)));
    }

    /**
     * Returns an immutable set of all currently registered custom operator codes.
     * <p>
     * This method is useful for introspection, diagnostics, and debugging purposes.
     * The returned set is a snapshot of the current registration state and will not
     * reflect subsequent changes to the registry.
     * </p>
     *
     * <p><strong>Example usage:</strong></p>
     * <pre>{@code
     * // Register some operators
     * OperatorProviderRegistry.register(new PhoneticOperatorProvider());
     * OperatorProviderRegistry.register(new GeospatialOperatorProvider());
     *
     * // Get all registered operators
     * Set<String> registeredOperators = OperatorProviderRegistry.getAllRegisteredOperators();
     * System.out.println("Registered operators: " + registeredOperators);
     * // Output: Registered operators: [SOUNDEX, GEO_DISTANCE, GEO_WITHIN_BOX]
     *
     * // Check if specific operator is available
     * if (registeredOperators.contains("SOUNDEX")) {
     *     // SOUNDEX operator is available
     * }
     * }</pre>
     *
     * @return an immutable set of all registered operator codes (in UPPERCASE)
     * @since 4.0.0
     */
    public static Set<String> getAllRegisteredOperators() {
        return Collections.unmodifiableSet(new HashSet<>(OPERATORS.keySet()));
    }

    /**
     * Clears all operator registrations, removing all associations
     * between operator codes and providers.
     * <p>
     * This method is primarily intended for testing or full reset scenarios.
     * </p>
     */
    public static void unregisterAll() {
        OPERATORS.clear();
    }
}
