package io.github.cyfko.filterql.core.spi;

import io.github.cyfko.filterql.core.exception.FilterValidationException;
import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.validation.PropertyReference;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive test suite for OperatorProviderRegistry.
 * Tests registration, retrieval, concurrency, and error handling for custom operators.
 *
 * @author FilterQL Test Suite
 * @since 4.0.0
 */
@DisplayName("OperatorProviderRegistry Tests")
class OperatorProviderRegistryTest {

    private CustomOperatorProvider testProvider1;
    private CustomOperatorProvider testProvider2;
    private CustomOperatorProvider multiOpProvider;

    @BeforeEach
    void setUp() {
        // Simple provider with one operator
        testProvider1 = new CustomOperatorProvider() {
            @Override
            public Set<String> supportedOperators() {
                return Set.of("TEST_OP_1");
            }

            @Override
            public <P extends Enum<P> & PropertyReference>
            PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
                return (root, query, cb) -> cb.conjunction();
            }
        };

        // Another simple provider
        testProvider2 = new CustomOperatorProvider() {
            @Override
            public Set<String> supportedOperators() {
                return Set.of("TEST_OP_2");
            }

            @Override
            public <P extends Enum<P> & PropertyReference>
            PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
                return (root, query, cb) -> cb.disjunction();
            }
        };

        // Provider supporting multiple operators
        multiOpProvider = new CustomOperatorProvider() {
            @Override
            public Set<String> supportedOperators() {
                return Set.of("MULTI_OP_1", "MULTI_OP_2", "MULTI_OP_3");
            }

            @Override
            public <P extends Enum<P> & PropertyReference>
            PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
                return (root, query, cb) -> cb.conjunction();
            }
        };

        OperatorProviderRegistry.unregister(testProvider1.supportedOperators());
        OperatorProviderRegistry.unregister(testProvider2.supportedOperators());
        OperatorProviderRegistry.unregister(multiOpProvider.supportedOperators());
    }

    // ============================================================================
    // Registration Tests
    // ============================================================================

    @Test
    @DisplayName("Should register custom operator provider successfully")
    void shouldRegisterCustomOperatorProvider() {
        // When
        assertDoesNotThrow(() -> OperatorProviderRegistry.register(testProvider1));

        // Then
        Optional<CustomOperatorProvider> retrieved =
            OperatorProviderRegistry.getProvider("TEST_OP_1");

        assertTrue(retrieved.isPresent(), "Provider should be registered");
        assertSame(testProvider1, retrieved.get(), "Retrieved provider should be the same instance");
    }

    @Test
    @DisplayName("Should register multiple different providers")
    void shouldRegisterMultipleDifferentProviders() {
        // When
        OperatorProviderRegistry.register(testProvider1);
        OperatorProviderRegistry.register(testProvider2);

        // Then
        Optional<CustomOperatorProvider> provider1 =
            OperatorProviderRegistry.getProvider("TEST_OP_1");
        Optional<CustomOperatorProvider> provider2 =
            OperatorProviderRegistry.getProvider("TEST_OP_2");

        assertTrue(provider1.isPresent(), "Provider 1 should be registered");
        assertTrue(provider2.isPresent(), "Provider 2 should be registered");
        assertSame(testProvider1, provider1.get());
        assertSame(testProvider2, provider2.get());
    }

    @Test
    @DisplayName("Should register provider with multiple operators")
    void shouldRegisterProviderWithMultipleOperators() {
        // When
        OperatorProviderRegistry.register(multiOpProvider);

        // Then
        Optional<CustomOperatorProvider> op1 = OperatorProviderRegistry.getProvider("MULTI_OP_1");
        Optional<CustomOperatorProvider> op2 = OperatorProviderRegistry.getProvider("MULTI_OP_2");
        Optional<CustomOperatorProvider> op3 = OperatorProviderRegistry.getProvider("MULTI_OP_3");

        assertTrue(op1.isPresent(), "MULTI_OP_1 should be registered");
        assertTrue(op2.isPresent(), "MULTI_OP_2 should be registered");
        assertTrue(op3.isPresent(), "MULTI_OP_3 should be registered");

        // All should point to the same provider instance
        assertSame(multiOpProvider, op1.get());
        assertSame(multiOpProvider, op2.get());
        assertSame(multiOpProvider, op3.get());
    }

    // ============================================================================
    // Error Handling Tests
    // ============================================================================

    @Test
    @DisplayName("Should throw IllegalArgumentException when registering duplicate operator")
    void shouldThrowExceptionWhenRegisteringDuplicate() {
        // Given
        OperatorProviderRegistry.register(testProvider1);

        // Create another provider with same operator code
        CustomOperatorProvider duplicateProvider = new CustomOperatorProvider() {
            @Override
            public Set<String> supportedOperators() {
                return Set.of("TEST_OP_1");
            }

            @Override
            public <P extends Enum<P> & PropertyReference>
            PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
                return (root, query, cb) -> cb.conjunction();
            }
        };

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> OperatorProviderRegistry.register(duplicateProvider),
            "Should throw IllegalArgumentException for duplicate operator"
        );

        assertTrue(exception.getMessage().contains("TEST_OP_1"),
            "Exception message should mention the duplicate operator");
        assertTrue(exception.getMessage().contains("already registered"),
            "Exception message should indicate operator is already registered");
    }

    @Test
    @DisplayName("Should throw NullPointerException when registering null provider")
    void shouldThrowExceptionWhenRegisteringNullProvider() {
        // When & Then
        assertThrows(
            NullPointerException.class,
            () -> OperatorProviderRegistry.register(null),
            "Should throw NullPointerException for null provider"
        );
    }

    @Test
    @DisplayName("Should throw exception when provider returns null for supportedOperators")
    void shouldThrowExceptionWhenProviderReturnsNullOperators() {
        // Given
        CustomOperatorProvider nullOperatorsProvider = new CustomOperatorProvider() {
            @Override
            public Set<String> supportedOperators() {
                return null;
            }

            @Override
            public <P extends Enum<P> & PropertyReference>
            PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
                return (root, query, cb) -> cb.conjunction();
            }
        };

        // When & Then
        assertThrows(
            NullPointerException.class,
            () -> OperatorProviderRegistry.register(nullOperatorsProvider),
            "Should throw exception when supportedOperators returns null"
        );
    }

    // ============================================================================
    // Retrieval Tests
    // ============================================================================

    @Test
    @DisplayName("Should return empty Optional for unknown operator")
    void shouldReturnEmptyOptionalForUnknownOperator() {
        // When
        Optional<CustomOperatorProvider> provider =
            OperatorProviderRegistry.getProvider("UNKNOWN_OPERATOR");

        // Then
        assertTrue(provider.isEmpty(), "Should return empty Optional for unknown operator");
    }

    @Test
    @DisplayName("Should return empty Optional for null operator code")
    void shouldReturnEmptyOptionalForNullOperator() {
        // When
        Optional<CustomOperatorProvider> provider =
            OperatorProviderRegistry.getProvider(null);

        // Then
        assertTrue(provider.isEmpty(), "Should return empty Optional for null operator code");
    }

    @Test
    @DisplayName("Should handle case-sensitive operator lookup")
    void shouldHandleCaseSensitiveOperatorLookup() {
        // Given
        OperatorProviderRegistry.register(testProvider1);

        // When
        Optional<CustomOperatorProvider> anyCase =
            OperatorProviderRegistry.getProvider("teSt_oP_1");
        Optional<CustomOperatorProvider> upperCase =
            OperatorProviderRegistry.getProvider("TEST_OP_1");

        // Then
        assertEquals(anyCase, upperCase);
    }

    // ============================================================================
    // Concurrency Tests
    // ============================================================================

    @Test
    @DisplayName("Should handle concurrent registrations safely")
    void shouldHandleConcurrentRegistrationsSafely() throws InterruptedException {
        // Given
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Create threads that try to register providers concurrently
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            new Thread(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready

                    CustomOperatorProvider provider = new CustomOperatorProvider() {
                        @Override
                        public Set<String> supportedOperators() {
                            return Set.of("CONCURRENT_OP_" + index);
                        }

                        @Override
                        public <P extends Enum<P> & PropertyReference>
                        PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
                            return (root, query, cb) -> cb.conjunction();
                        }
                    };

                    OperatorProviderRegistry.register(provider);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        // When
        startLatch.countDown(); // Start all threads simultaneously
        doneLatch.await(); // Wait for all threads to complete

        // Then
        assertEquals(threadCount, successCount.get(),
            "All concurrent registrations should succeed");
        assertEquals(0, failureCount.get(),
            "No failures should occur during concurrent registration");

        // Verify all operators are registered
        for (int i = 0; i < threadCount; i++) {
            Optional<CustomOperatorProvider> provider =
                OperatorProviderRegistry.getProvider("CONCURRENT_OP_" + i);
            assertTrue(provider.isPresent(),
                "Operator CONCURRENT_OP_" + i + " should be registered");
        }
    }

    @Test
    @DisplayName("Should handle concurrent duplicate registration attempts")
    void shouldHandleConcurrentDuplicateRegistrationAttempts() throws InterruptedException {
        // Given
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        // Create threads that try to register the same operator
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();

                    CustomOperatorProvider provider = new CustomOperatorProvider() {
                        @Override
                        public Set<String> supportedOperators() {
                            return Set.of("DUPLICATE_OP");
                        }

                        @Override
                        public <P extends Enum<P> & PropertyReference>
                        PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
                            return (root, query, cb) -> cb.conjunction();
                        }
                    };

                    OperatorProviderRegistry.register(provider);
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException | InterruptedException e) {
                    duplicateCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        // When
        startLatch.countDown();
        doneLatch.await();

        // Then
        assertEquals(1, successCount.get(),
            "Exactly one registration should succeed");
        assertEquals(threadCount - 1, duplicateCount.get(),
            "All other attempts should fail with duplicate exception");
    }

    // ============================================================================
    // Edge Cases
    // ============================================================================

    @Test
    @DisplayName("Should handle provider with empty operator set")
    void shouldHandleProviderWithEmptyOperatorSet() {
        // Given
        CustomOperatorProvider emptyProvider = new CustomOperatorProvider() {
            @Override
            public Set<String> supportedOperators() {
                return Set.of();
            }

            @Override
            public <P extends Enum<P> & PropertyReference>
            PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
                return (root, query, cb) -> cb.conjunction();
            }
        };

        // When & Then
        assertDoesNotThrow(() -> OperatorProviderRegistry.register(emptyProvider),
            "Should allow registration of provider with empty operator set");
    }

    @Test
    @DisplayName("Should handle operator codes with special characters")
    void shouldHandleOperatorCodesWithSpecialCharacters() {
        // Given
        CustomOperatorProvider specialProvider = new CustomOperatorProvider() {
            @Override
            public Set<String> supportedOperators() {
                return Set.of("OP-WITH-DASH", "OP_WITH_UNDERSCORE", "OP.WITH.DOT");
            }

            @Override
            public <P extends Enum<P> & PropertyReference>
            PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
                return (root, query, cb) -> cb.conjunction();
            }
        };

        // When
        OperatorProviderRegistry.register(specialProvider);

        // Then
        assertTrue(OperatorProviderRegistry.getProvider("OP-WITH-DASH").isPresent());
        assertTrue(OperatorProviderRegistry.getProvider("OP_WITH_UNDERSCORE").isPresent());
        assertTrue(OperatorProviderRegistry.getProvider("OP.WITH.DOT").isPresent());
    }

    @Test
    @DisplayName("Should maintain provider independence")
    void shouldMaintainProviderIndependence() {
        // Given
        OperatorProviderRegistry.register(testProvider1);
        OperatorProviderRegistry.register(testProvider2);

        // When
        Optional<CustomOperatorProvider> provider1 =
            OperatorProviderRegistry.getProvider("TEST_OP_1");
        Optional<CustomOperatorProvider> provider2 =
            OperatorProviderRegistry.getProvider("TEST_OP_2");

        // Then
        assertTrue(provider1.isPresent());
        assertTrue(provider2.isPresent());
        assertNotSame(provider1.get(), provider2.get(),
            "Different providers should be independent instances");
    }

    // ============================================================================
    // Bulk Unregistration Tests
    // ============================================================================

    @Test
    @DisplayName("Should unregister all operators when calling unregisterAll()")
    void shouldUnregisterAllOperators() {
        // Given - Register multiple providers
        OperatorProviderRegistry.register(testProvider1);
        OperatorProviderRegistry.register(testProvider2);
        OperatorProviderRegistry.register(multiOpProvider);

        // Verify operators are registered
        assertTrue(OperatorProviderRegistry.getProvider("TEST_OP_1").isPresent());
        assertTrue(OperatorProviderRegistry.getProvider("TEST_OP_2").isPresent());
        assertTrue(OperatorProviderRegistry.getProvider("MULTI_OP_1").isPresent());
        assertTrue(OperatorProviderRegistry.getProvider("MULTI_OP_2").isPresent());
        assertTrue(OperatorProviderRegistry.getProvider("MULTI_OP_3").isPresent());

        Set<String> registeredBefore = OperatorProviderRegistry.getAllRegisteredOperators();
        assertFalse(registeredBefore.isEmpty(), "Registry should have operators before unregisterAll");

        // When - Clear all registrations
        OperatorProviderRegistry.unregisterAll();

        // Then - All operators should be gone
        assertTrue(OperatorProviderRegistry.getProvider("TEST_OP_1").isEmpty(),
            "TEST_OP_1 should be unregistered");
        assertTrue(OperatorProviderRegistry.getProvider("TEST_OP_2").isEmpty(),
            "TEST_OP_2 should be unregistered");
        assertTrue(OperatorProviderRegistry.getProvider("MULTI_OP_1").isEmpty(),
            "MULTI_OP_1 should be unregistered");
        assertTrue(OperatorProviderRegistry.getProvider("MULTI_OP_2").isEmpty(),
            "MULTI_OP_2 should be unregistered");
        assertTrue(OperatorProviderRegistry.getProvider("MULTI_OP_3").isEmpty(),
            "MULTI_OP_3 should be unregistered");

        Set<String> registeredAfter = OperatorProviderRegistry.getAllRegisteredOperators();
        assertTrue(registeredAfter.isEmpty(), "Registry should be empty after unregisterAll");
    }

    @Test
    @DisplayName("Should be safe to call unregisterAll() on empty registry")
    void shouldHandleUnregisterAllOnEmptyRegistry() {
        // Given - Ensure registry is empty
        OperatorProviderRegistry.unregisterAll();

        // When & Then - Should not throw
        assertDoesNotThrow(() -> OperatorProviderRegistry.unregisterAll(),
            "unregisterAll() should be safe on empty registry");

        // Verify still empty
        Set<String> registered = OperatorProviderRegistry.getAllRegisteredOperators();
        assertTrue(registered.isEmpty(), "Registry should remain empty");
    }

    @Test
    @DisplayName("Should allow re-registration after unregisterAll()")
    void shouldAllowReRegistrationAfterUnregisterAll() {
        // Given - Register and then clear
        OperatorProviderRegistry.register(testProvider1);
        OperatorProviderRegistry.unregisterAll();

        // When - Re-register same provider
        assertDoesNotThrow(() -> OperatorProviderRegistry.register(testProvider1),
            "Should allow re-registration after unregisterAll");

        // Then - Provider should be available again
        Optional<CustomOperatorProvider> provider =
            OperatorProviderRegistry.getProvider("TEST_OP_1");
        assertTrue(provider.isPresent(), "Provider should be registered again");
        assertSame(testProvider1, provider.get(),
            "Retrieved provider should be the same instance");
    }
}
