package io.github.cyfko.filterql.core.spi;

import io.github.cyfko.filterql.core.exception.FilterValidationException;
import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.validation.PropertyReference;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load and stress tests for OperatorProviderRegistry.
 * Tests thread-safety, concurrent registrations, and high-load scenarios.
 */
@DisplayName("OperatorProviderRegistry Load Tests")
class OperatorProviderRegistryLoadTest {

    private List<CustomOperatorProvider> registeredProviders;

    @BeforeEach
    void setUp() {
        registeredProviders = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        // Clean up all registered providers
        for (CustomOperatorProvider provider : registeredProviders) {
            try {
                OperatorProviderRegistry.unregister(provider);
            } catch (Exception ignored) {
                // May have been already unregistered
            }
        }
        registeredProviders.clear();
    }

    // ============================================================================
    // Concurrent Registration Tests
    // ============================================================================

    @Test
    @DisplayName("Should handle concurrent registrations from multiple threads")
    void shouldHandleConcurrentRegistrationsFromMultipleThreads() throws InterruptedException {
        // Given
        int numThreads = 20;
        int providersPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        ConcurrentLinkedQueue<CustomOperatorProvider> allProviders = new ConcurrentLinkedQueue<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < providersPerThread; i++) {
                        String opCode = "CONCURRENT_REG_T" + threadId + "_OP" + i;
                        CustomOperatorProvider provider = createProvider(opCode);
                        try {
                            OperatorProviderRegistry.register(provider);
                            allProviders.add(provider);
                            successCount.incrementAndGet();
                        } catch (IllegalArgumentException e) {
                            failureCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Cleanup
        registeredProviders.addAll(allProviders);

        // Then
        int expectedSuccess = numThreads * providersPerThread;
        System.out.printf("✓ Concurrent registrations: %d success, %d failures out of %d attempts%n",
            successCount.get(), failureCount.get(), expectedSuccess);

        assertEquals(expectedSuccess, successCount.get(), "All registrations should succeed with unique operator codes");
        assertEquals(0, failureCount.get(), "No failures should occur");

        // Verify all operators are accessible
        for (CustomOperatorProvider provider : allProviders) {
            String opCode = provider.supportedOperators().iterator().next();
            Optional<CustomOperatorProvider> retrieved = OperatorProviderRegistry.getProvider(opCode);
            assertTrue(retrieved.isPresent(), "Operator " + opCode + " should be retrievable");
        }
    }

    @Test
    @DisplayName("Should detect concurrent duplicate registrations")
    @Disabled
    void shouldDetectConcurrentDuplicateRegistrations() throws InterruptedException {
        // Given - Multiple threads trying to register the SAME operator
        int numThreads = 10;
        String sharedOperatorCode = "DUPLICATE_OP";
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<CustomOperatorProvider> successfulProviders = Collections.synchronizedList(new ArrayList<>());

        // When - All threads try to register simultaneously
        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for signal to start
                    CustomOperatorProvider provider = createProvider(sharedOperatorCode);
                    try {
                        OperatorProviderRegistry.register(provider);
                        successCount.incrementAndGet();
                        successfulProviders.add(provider);
                    } catch (IllegalArgumentException e) {
                        failureCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Signal all threads to start
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Cleanup
        registeredProviders.addAll(successfulProviders);

        // Then
        System.out.printf("✓ Duplicate registration attempts: 1 success, %d failures (expected)%n", failureCount.get());

        assertEquals(1, successCount.get(), "Only ONE thread should succeed in registering");
        assertEquals(numThreads - 1, failureCount.get(), "All other threads should fail with IllegalArgumentException");
    }

    @Test
    @DisplayName("Should handle concurrent registration and lookup")
    void shouldHandleConcurrentRegistrationAndLookup() throws InterruptedException {
        // Given
        int numRegistrationThreads = 5;
        int numLookupThreads = 10;
        int operatorsPerRegThread = 20;
        int lookupsPerLookupThread = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(numRegistrationThreads + numLookupThreads);
        CountDownLatch latch = new CountDownLatch(numRegistrationThreads + numLookupThreads);
        ConcurrentLinkedQueue<CustomOperatorProvider> providers = new ConcurrentLinkedQueue<>();
        AtomicInteger lookupSuccessCount = new AtomicInteger(0);

        // When - Registration threads
        for (int t = 0; t < numRegistrationThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operatorsPerRegThread; i++) {
                        String opCode = "REG_LOOKUP_T" + threadId + "_" + i;
                        CustomOperatorProvider provider = createProvider(opCode);
                        OperatorProviderRegistry.register(provider);
                        providers.add(provider);
                        Thread.sleep(1); // Small delay to simulate real-world scenarios
                    }
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        // When - Lookup threads (running simultaneously with registration)
        for (int t = 0; t < numLookupThreads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < lookupsPerLookupThread; i++) {
                        int randomThread = ThreadLocalRandom.current().nextInt(numRegistrationThreads);
                        int randomOp = ThreadLocalRandom.current().nextInt(operatorsPerRegThread);
                        String opCode = "REG_LOOKUP_T" + randomThread + "_" + randomOp;

                        Optional<CustomOperatorProvider> result = OperatorProviderRegistry.getProvider(opCode);
                        if (result.isPresent()) {
                            lookupSuccessCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Cleanup
        registeredProviders.addAll(providers);

        // Then
        System.out.printf("✓ Concurrent reg+lookup: %d providers registered, %d successful lookups%n",
            providers.size(), lookupSuccessCount.get());

        assertTrue(providers.size() > 0, "Some providers should be registered");
        assertTrue(lookupSuccessCount.get() > 0, "Some lookups should succeed");
    }

    // ============================================================================
    // Concurrent Unregistration Tests
    // ============================================================================

    @Test
    @DisplayName("Should handle concurrent unregistrations safely")
    void shouldHandleConcurrentUnregistrationsSafely() throws InterruptedException {
        // Given - Register 100 operators first
        List<CustomOperatorProvider> providers = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            CustomOperatorProvider provider = createProvider("UNREG_OP_" + i);
            OperatorProviderRegistry.register(provider);
            providers.add(provider);
        }

        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger unregisteredCount = new AtomicInteger(0);

        // When - Concurrent unregistration
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    // Each thread unregisters 10 operators
                    for (int i = 0; i < 10; i++) {
                        int opIndex = threadId * 10 + i;
                        CustomOperatorProvider provider = providers.get(opIndex);
                        OperatorProviderRegistry.unregister(provider);
                        unregisteredCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        System.out.printf("✓ Concurrent unregistrations: %d operators unregistered%n", unregisteredCount.get());

        assertEquals(100, unregisteredCount.get(), "All 100 operators should be unregistered");

        // Verify none are accessible
        for (int i = 0; i < 100; i++) {
            Optional<CustomOperatorProvider> result = OperatorProviderRegistry.getProvider("UNREG_OP_" + i);
            assertFalse(result.isPresent(), "Operator UNREG_OP_" + i + " should not be accessible after unregistration");
        }
    }

    @Test
    @DisplayName("Should handle concurrent register and unregister of same operator")
    void shouldHandleConcurrentRegisterAndUnregisterOfSameOperator() throws InterruptedException {
        // Given
        int numIterations = 100;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        String operatorCode = "REG_UNREG_OP";
        AtomicInteger registrationSuccesses = new AtomicInteger(0);
        AtomicInteger registrationFailures = new AtomicInteger(0);
        List<CustomOperatorProvider> successfulProviders = Collections.synchronizedList(new ArrayList<>());

        // When - Thread 1: Continuously registers
        executor.submit(() -> {
            try {
                for (int i = 0; i < numIterations; i++) {
                    CustomOperatorProvider provider = createProvider(operatorCode);
                    try {
                        OperatorProviderRegistry.register(provider);
                        registrationSuccesses.incrementAndGet();
                        successfulProviders.add(provider);
                        Thread.sleep(5); // Small delay
                    } catch (IllegalArgumentException e) {
                        registrationFailures.incrementAndGet();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });

        // Thread 2: Continuously unregisters
        executor.submit(() -> {
            try {
                for (int i = 0; i < numIterations; i++) {
                    OperatorProviderRegistry.unregister(Set.of(operatorCode));
                    Thread.sleep(5); // Small delay
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Cleanup
        OperatorProviderRegistry.unregister(Set.of(operatorCode));

        // Then
        System.out.printf("✓ Concurrent reg/unreg: %d registration successes, %d failures%n",
            registrationSuccesses.get(), registrationFailures.get());

        assertTrue(registrationSuccesses.get() > 0, "Some registrations should succeed");
        // Some registrations may fail due to concurrent state
        assertTrue(registrationSuccesses.get() + registrationFailures.get() == numIterations,
            "Total attempts should match iterations");
    }

    // ============================================================================
    // High Load Stress Tests
    // ============================================================================

    @Test
    @DisplayName("Should handle high-volume mixed operations under stress")
    void shouldHandleHighVolumeMixedOperationsUnderStress() throws InterruptedException {
        // Given
        int numThreads = 20;
        int operationsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        ConcurrentLinkedQueue<CustomOperatorProvider> providers = new ConcurrentLinkedQueue<>();

        AtomicInteger registerOps = new AtomicInteger(0);
        AtomicInteger lookupOps = new AtomicInteger(0);
        AtomicInteger unregisterOps = new AtomicInteger(0);

        // When - Each thread performs random mix of operations
        long startTime = System.nanoTime();

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    for (int i = 0; i < operationsPerThread; i++) {
                        int operation = random.nextInt(10);

                        if (operation < 3) {
                            // 30% - Register
                            String opCode = "STRESS_T" + threadId + "_OP" + i;
                            CustomOperatorProvider provider = createProvider(opCode);
                            try {
                                OperatorProviderRegistry.register(provider);
                                providers.add(provider);
                                registerOps.incrementAndGet();
                            } catch (IllegalArgumentException ignored) {}

                        } else if (operation < 8) {
                            // 50% - Lookup
                            int randomThread = random.nextInt(numThreads);
                            int randomOp = random.nextInt(operationsPerThread);
                            String opCode = "STRESS_T" + randomThread + "_OP" + randomOp;
                            OperatorProviderRegistry.getProvider(opCode);
                            lookupOps.incrementAndGet();

                        } else {
                            // 20% - Unregister
                            int randomThread = random.nextInt(numThreads);
                            int randomOp = random.nextInt(operationsPerThread);
                            String opCode = "STRESS_T" + randomThread + "_OP" + randomOp;
                            OperatorProviderRegistry.unregister(Set.of(opCode));
                            unregisterOps.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        long endTime = System.nanoTime();
        executor.shutdown();

        // Cleanup
        for (CustomOperatorProvider provider : providers) {
            try {
                OperatorProviderRegistry.unregister(provider);
            } catch (Exception ignored) {}
        }

        // Then
        long durationMs = (endTime - startTime) / 1_000_000;
        int totalOps = registerOps.get() + lookupOps.get() + unregisterOps.get();
        double opsPerSecond = (double) totalOps / durationMs * 1000;

        System.out.printf("✓ Stress test: %,d total operations in %,d ms (%.0f ops/sec)%n",
            totalOps, durationMs, opsPerSecond);
        System.out.printf("  - Registers: %,d%n", registerOps.get());
        System.out.printf("  - Lookups: %,d%n", lookupOps.get());
        System.out.printf("  - Unregisters: %,d%n", unregisterOps.get());

        assertTrue(totalOps > 0, "Should complete some operations");
        assertTrue(durationMs < 30_000, "Should complete stress test in < 30 seconds");
    }

    @Test
    @DisplayName("Should maintain consistency under high contention")
    @Disabled
    void shouldMaintainConsistencyUnderHighContention() throws InterruptedException {
        // Given - High contention on small set of operators
        int numThreads = 50;
        int numOperators = 5; // Small number = high contention
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger consistencyErrors = new AtomicInteger(0);

        // When - Many threads competing for same operators
        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        int opIndex = ThreadLocalRandom.current().nextInt(numOperators);
                        String opCode = "CONTENTION_OP_" + opIndex;

                        // Try to register
                        CustomOperatorProvider provider = createProvider(opCode);
                        try {
                            OperatorProviderRegistry.register(provider);

                            // Immediately verify it's accessible
                            Optional<CustomOperatorProvider> retrieved = OperatorProviderRegistry.getProvider(opCode);
                            if (!retrieved.isPresent()) {
                                consistencyErrors.incrementAndGet();
                            }

                            // Unregister
                            OperatorProviderRegistry.unregister(Set.of(opCode));

                            // Verify it's not accessible
                            retrieved = OperatorProviderRegistry.getProvider(opCode);
                            if (retrieved.isPresent()) {
                                consistencyErrors.incrementAndGet();
                            }

                        } catch (IllegalArgumentException ignored) {
                            // Expected - another thread may have registered it
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        System.out.printf("✓ High contention test: %d consistency errors (expected: 0)%n", consistencyErrors.get());

        assertEquals(0, consistencyErrors.get(),
            "No consistency errors - registry should maintain correctness under high contention");
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
