package io.github.cyfko.filterql.core.spi;

import io.github.cyfko.filterql.core.exception.FilterValidationException;
import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.validation.PropertyReference;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for OperatorProviderRegistry.
 * Tests registry lookup performance with varying numbers of registered operators.
 */
@DisplayName("OperatorProviderRegistry Performance Tests")
class OperatorProviderRegistryPerformanceTest {

    private List<CustomOperatorProvider> registeredProviders;

    @BeforeEach
    void setUp() {
        registeredProviders = new ArrayList<>();
        OperatorProviderRegistry.unregisterAll();
    }

    @AfterEach
    void tearDown() {
        // Clean up all registered providers
        for (CustomOperatorProvider provider : registeredProviders) {
            OperatorProviderRegistry.unregister(provider);
        }
        registeredProviders.clear();
    }

    // ============================================================================
    // Lookup Performance Tests
    // ============================================================================

    @Test
    @DisplayName("Should perform fast lookups with 1 registered operator")
    void shouldPerformFastLookupsWithOneOperator() {
        // Given
        CustomOperatorProvider provider = createProvider("CUSTOM_OP_1");
        OperatorProviderRegistry.register(provider);
        registeredProviders.add(provider);

        int iterations = 100_000;

        // When
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            Optional<CustomOperatorProvider> result = OperatorProviderRegistry.getProvider("CUSTOM_OP_1");
            assertTrue(result.isPresent());
        }
        long endTime = System.nanoTime();

        // Then
        long durationMs = (endTime - startTime) / 1_000_000;
        double avgMicroseconds = (double)(endTime - startTime) / iterations / 1000;

        System.out.printf("✓ %,d lookups with 1 operator: %,d ms (avg: %.3f μs per lookup)%n",
            iterations, durationMs, avgMicroseconds);

        // Assert reasonable performance (should be very fast with 1 operator)
        assertTrue(durationMs < 1000, "Should complete 100K lookups in less than 1 second");
        assertTrue(avgMicroseconds < 10, "Average lookup should be < 10 microseconds");
    }

    @Test
    @DisplayName("Should perform fast lookups with 10 registered operators")
    void shouldPerformFastLookupsWithTenOperators() {
        // Given
        for (int i = 1; i <= 10; i++) {
            CustomOperatorProvider provider = createProvider("CUSTOM_OP_" + i);
            OperatorProviderRegistry.register(provider);
            registeredProviders.add(provider);
        }

        int iterations = 100_000;

        // When - Test lookup of first, middle, and last operators
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            OperatorProviderRegistry.getProvider("CUSTOM_OP_1");  // First
            OperatorProviderRegistry.getProvider("CUSTOM_OP_5");  // Middle
            OperatorProviderRegistry.getProvider("CUSTOM_OP_10"); // Last
        }
        long endTime = System.nanoTime();

        // Then
        long durationMs = (endTime - startTime) / 1_000_000;
        double avgMicroseconds = (double)(endTime - startTime) / (iterations * 3) / 1000;

        System.out.printf("✓ %,d lookups with 10 operators: %,d ms (avg: %.3f μs per lookup)%n",
            iterations * 3, durationMs, avgMicroseconds);

        assertTrue(durationMs < 2000, "Should complete 300K lookups in less than 2 seconds");
    }

    @Test
    @DisplayName("Should perform well with 100 registered operators")
    void shouldPerformWellWithHundredOperators() {
        // Given
        for (int i = 1; i <= 100; i++) {
            CustomOperatorProvider provider = createProvider("CUSTOM_OP_" + i);
            OperatorProviderRegistry.register(provider);
            registeredProviders.add(provider);
        }

        int iterations = 50_000;

        // When
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            OperatorProviderRegistry.getProvider("CUSTOM_OP_50"); // Middle operator
        }
        long endTime = System.nanoTime();

        // Then
        long durationMs = (endTime - startTime) / 1_000_000;
        double avgMicroseconds = (double)(endTime - startTime) / iterations / 1000;

        System.out.printf("✓ %,d lookups with 100 operators: %,d ms (avg: %.3f μs per lookup)%n",
            iterations, durationMs, avgMicroseconds);

        // ConcurrentHashMap should maintain O(1) performance even with 100 operators
        assertTrue(avgMicroseconds < 20, "Average lookup should remain < 20 microseconds");
    }

    @Test
    @DisplayName("Should handle cache misses efficiently")
    void shouldHandleCacheMissesEfficiently() {
        // Given
        for (int i = 1; i <= 50; i++) {
            CustomOperatorProvider provider = createProvider("REGISTERED_OP_" + i);
            OperatorProviderRegistry.register(provider);
            registeredProviders.add(provider);
        }

        int iterations = 100_000;

        // When - Lookup non-existent operators (cache misses)
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            Optional<CustomOperatorProvider> result = OperatorProviderRegistry.getProvider("NON_EXISTENT_OP");
            assertFalse(result.isPresent());
        }
        long endTime = System.nanoTime();

        // Then
        long durationMs = (endTime - startTime) / 1_000_000;
        double avgMicroseconds = (double)(endTime - startTime) / iterations / 1000;

        System.out.printf("✓ %,d cache misses: %,d ms (avg: %.3f μs per miss)%n",
            iterations, durationMs, avgMicroseconds);

        // Cache misses should be as fast as hits with ConcurrentHashMap
        assertTrue(avgMicroseconds < 10, "Cache misses should be < 10 microseconds");
    }

    // ============================================================================
    // Registration Performance Tests
    // ============================================================================

    @Test
    @DisplayName("Should register 100 operators quickly")
    void shouldRegisterHundredOperatorsQuickly() {
        // When
        long startTime = System.nanoTime();
        for (int i = 1; i <= 100; i++) {
            CustomOperatorProvider provider = createProvider("REG_OP_" + i);
            OperatorProviderRegistry.register(provider);
            registeredProviders.add(provider);
        }
        long endTime = System.nanoTime();

        // Then
        long durationMs = (endTime - startTime) / 1_000_000;
        System.out.printf("✓ Registered 100 operators in %,d ms%n", durationMs);

        assertTrue(durationMs < 100, "Should register 100 operators in < 100ms");
    }

    @Test
    @DisplayName("Should unregister 100 operators quickly")
    void shouldUnregisterHundredOperatorsQuickly() {
        // Given
        for (int i = 1; i <= 100; i++) {
            CustomOperatorProvider provider = createProvider("UNREG_OP_" + i);
            OperatorProviderRegistry.register(provider);
            registeredProviders.add(provider);
        }

        // When
        long startTime = System.nanoTime();
        for (CustomOperatorProvider provider : registeredProviders) {
            OperatorProviderRegistry.unregister(provider);
        }
        long endTime = System.nanoTime();

        // Then
        long durationMs = (endTime - startTime) / 1_000_000;
        System.out.printf("✓ Unregistered 100 operators in %,d ms%n", durationMs);

        assertTrue(durationMs < 100, "Should unregister 100 operators in < 100ms");
    }

    // ============================================================================
    // Concurrent Access Performance Tests
    // ============================================================================

    @Test
    @DisplayName("Should handle concurrent lookups efficiently")
    void shouldHandleConcurrentLookupsEfficiently() throws InterruptedException {
        // Given
        for (int i = 1; i <= 20; i++) {
            CustomOperatorProvider provider = createProvider("CONCURRENT_OP_" + i);
            OperatorProviderRegistry.register(provider);
            registeredProviders.add(provider);
        }

        int numThreads = 10;
        int lookupsPerThread = 10_000;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        // When
        long startTime = System.nanoTime();
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < lookupsPerThread; i++) {
                        String opCode = "CONCURRENT_OP_" + ((i % 20) + 1);
                        Optional<CustomOperatorProvider> result = OperatorProviderRegistry.getProvider(opCode);
                        assertTrue(result.isPresent());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        long endTime = System.nanoTime();
        executor.shutdown();

        // Then
        long durationMs = (endTime - startTime) / 1_000_000;
        int totalLookups = numThreads * lookupsPerThread;
        double throughput = (double) totalLookups / durationMs * 1000;

        System.out.printf("✓ %,d concurrent lookups (%d threads × %,d lookups): %,d ms (%.0f lookups/sec)%n",
            totalLookups, numThreads, lookupsPerThread, durationMs, throughput);

        assertTrue(durationMs < 5000, "Should complete 100K concurrent lookups in < 5 seconds");
    }

    @Test
    @DisplayName("Should handle mixed concurrent operations efficiently")
    void shouldHandleMixedConcurrentOperationsEfficiently() throws InterruptedException {
        // Given
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        ConcurrentLinkedQueue<CustomOperatorProvider> threadProviders = new ConcurrentLinkedQueue<>();

        // When - Mix of register, lookup, unregister operations
        long startTime = System.nanoTime();
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    // Each thread registers 10 operators
                    for (int i = 0; i < 10; i++) {
                        CustomOperatorProvider provider = createProvider("MIXED_OP_T" + threadId + "_" + i);
                        OperatorProviderRegistry.register(provider);
                        threadProviders.add(provider);
                    }

                    // Perform lookups
                    for (int i = 0; i < 1000; i++) {
                        OperatorProviderRegistry.getProvider("MIXED_OP_T" + threadId + "_" + (i % 10));
                    }

                    // Unregister half
                    for (int i = 0; i < 5; i++) {
                        OperatorProviderRegistry.unregister(Set.of("MIXED_OP_T" + threadId + "_" + i));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        long endTime = System.nanoTime();
        executor.shutdown();

        // Cleanup remaining
        for (CustomOperatorProvider provider : threadProviders) {
            try {
                OperatorProviderRegistry.unregister(provider);
            } catch (Exception ignored) {}
        }

        // Then
        long durationMs = (endTime - startTime) / 1_000_000;
        System.out.printf("✓ Mixed concurrent operations (%d threads): %,d ms%n", numThreads, durationMs);

        assertTrue(durationMs < 3000, "Mixed operations should complete in < 3 seconds");
    }

    // ============================================================================
    // Case Normalization Performance
    // ============================================================================

    @Test
    @DisplayName("Should handle case-insensitive lookups efficiently")
    void shouldHandleCaseInsensitiveLookupsEfficiently() {
        // Given
        CustomOperatorProvider provider = createProvider("MIXED_CASE_OP");
        OperatorProviderRegistry.register(provider);
        registeredProviders.add(provider);

        int iterations = 100_000;

        // When - Test different case variations
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            OperatorProviderRegistry.getProvider("MIXED_CASE_OP");  // Uppercase
            OperatorProviderRegistry.getProvider("mixed_case_op");  // Lowercase
            OperatorProviderRegistry.getProvider("MiXeD_CaSe_Op");  // Mixed
        }
        long endTime = System.nanoTime();

        // Then
        long durationMs = (endTime - startTime) / 1_000_000;
        double avgMicroseconds = (double)(endTime - startTime) / (iterations * 3) / 1000;

        System.out.printf("✓ %,d case-insensitive lookups: %,d ms (avg: %.3f μs per lookup)%n",
            iterations * 3, durationMs, avgMicroseconds);

        // Case normalization should add minimal overhead
        assertTrue(avgMicroseconds < 15, "Case-insensitive lookups should be < 15 microseconds");
    }

    // ============================================================================
    // Memory and Scalability Tests
    // ============================================================================

    @Test
    @DisplayName("Should scale to 1000 operators without significant slowdown")
    void shouldScaleToThousandOperators() {
        // Given & When - Register 1000 operators and measure lookup time
        for (int i = 1; i <= 1000; i++) {
            CustomOperatorProvider provider = createProvider("SCALE_OP_" + i);
            OperatorProviderRegistry.register(provider);
            registeredProviders.add(provider);
        }

        int iterations = 10_000;
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            OperatorProviderRegistry.getProvider("SCALE_OP_500"); // Middle operator
        }
        long endTime = System.nanoTime();

        // Then
        long durationMs = (endTime - startTime) / 1_000_000;
        double avgMicroseconds = (double)(endTime - startTime) / iterations / 1000;

        System.out.printf("✓ %,d lookups with 1000 operators: %,d ms (avg: %.3f μs per lookup)%n",
            iterations, durationMs, avgMicroseconds);

        // ConcurrentHashMap O(1) lookup should scale well
        assertTrue(avgMicroseconds < 30, "Lookups should remain < 30 microseconds even with 1000 operators");
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private CustomOperatorProvider createProvider(String operatorCode) {
        return new CustomOperatorProvider() {
            @Override
            public Set<String> supportedOperators() {
                return Set.of(operatorCode);
            }

            @Override
            public <P extends Enum<P> & PropertyReference>
            PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
                return (root, query, cb) -> cb.conjunction();
            }
        };
    }
}
