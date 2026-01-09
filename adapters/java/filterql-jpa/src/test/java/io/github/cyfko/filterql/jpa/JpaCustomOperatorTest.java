package io.github.cyfko.filterql.jpa;

import io.github.cyfko.filterql.core.FilterQueryFactory;
import io.github.cyfko.filterql.core.spi.FilterQuery;
import io.github.cyfko.filterql.core.spi.OperatorProviderRegistry;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.core.spi.CustomOperatorProvider;
import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.core.validation.PropertyReference;
import io.github.cyfko.filterql.jpa.entities.document.Document;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CustomOperatorProvider integration with JPA FilterQL.
 * Validates registration, execution, validation, and error handling of custom operators.
 */
@DisplayName("JPA Custom Operator Tests")
class JpaCustomOperatorTest {

    private static EntityManagerFactory emf;
    private EntityManager em;
    private JpaFilterContext<DocumentProperty> context;

    @BeforeAll
    static void setUpClass() {
        Map<String, String> properties = new HashMap<>();
        properties.put("jakarta.persistence.jdbc.url", "jdbc:h2:mem:testdb");
        properties.put("jakarta.persistence.jdbc.driver", "org.h2.Driver");
        properties.put("jakarta.persistence.jdbc.user", "sa");
        properties.put("jakarta.persistence.jdbc.password", "");
        properties.put("jakarta.persistence.schema-generation.database.action", "create");
        properties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");

        emf = Persistence.createEntityManagerFactory("testPU", properties);
        
        // Setup test data once for all tests
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        
        // Documents with various characteristics for testing
        createDocument(em, "Java Programming Basics", "Introduction to Java programming", 150);
        createDocument(em, "Java 17 Features", "New features in Java 17", 250);
        createDocument(em, "JavaScript Guide", "Complete guide to JavaScript", 300);
        createDocument(em, "Python Tutorial", "Learn Python from scratch", 500);
        createDocument(em, "Database Design", "Principles of database design", 450);
        createDocument(em, "Short Doc", "Very short document", 50);
        createDocument(em, "Medium Article", "A medium-length article", 200);
        createDocument(em, "Long Essay", "A comprehensive essay on various topics", 1000);
        
        em.getTransaction().commit();
        em.close();
    }

    @AfterAll
    static void tearDownClass() {
        if (emf != null) {
            emf.close();
        }
    }

    @BeforeEach
    void setUp() {
        em = emf.createEntityManager();

        context = new JpaFilterContext<>(
                DocumentProperty.class,
                ref -> switch (ref) {
                    case TITLE -> "title";
                    case CONTENT -> "content";
                    case WORDCOUNT -> "wordCount"; // Changed from lowercase to camelCase
                }
        );
    }

    @AfterEach
    void tearDown() {
        // Unregister all custom operators
        OperatorProviderRegistry.unregisterAll();

        if (em != null && em.isOpen()) {
            em.close();
        }
    }

    // ============================================================================
    // Custom Operator Provider Implementations
    // ============================================================================

    /**
     * Custom operator that checks if a string field starts with a specific prefix.
     * Example: title STARTS_WITH "Java" matches "Java Programming", "Java 17", etc.
     */
    static class StartsWithOperatorProvider implements CustomOperatorProvider {

        @Override
        public Set<String> supportedOperators() {
            return Set.of("STARTS_WITH");
        }

        @Override
        public <P extends Enum<P> & PropertyReference>
        PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
            return (root, query, cb) -> {
                // Value validation at execution time
                if (!(definition.value() instanceof String prefix) || prefix.isBlank()) {
                    throw new IllegalArgumentException("STARTS_WITH requires non-blank String value");
                }
                
                // Map field name (handle WORDCOUNT → wordCount)
                String fieldName = definition.ref().name().equals("WORDCOUNT") 
                    ? "wordCount" 
                    : definition.ref().name().toLowerCase();
                
                return cb.like(root.get(fieldName), prefix + "%");
            };
        }
    }

    /**
     * Custom operator that checks if word count falls within a range.
     * Example: content WORD_COUNT_BETWEEN [100, 500] matches documents with 100-500 words.
     */
    static class WordCountBetweenOperatorProvider implements CustomOperatorProvider {

        @Override
        public Set<String> supportedOperators() {
            return Set.of("WORD_COUNT_BETWEEN");
        }

        @Override
        public <P extends Enum<P> & PropertyReference>
        PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
            return (root, query, cb) -> {
                // Value validation at execution time
                if (!(definition.value() instanceof List<?> list)) {
                    throw new IllegalArgumentException("WORD_COUNT_BETWEEN requires List with 2 elements [min, max]");
                }
                if (list.size() != 2) {
                    throw new IllegalArgumentException("WORD_COUNT_BETWEEN requires exactly 2 elements [min, max], got: " + list.size());
                }
                if (!(list.get(0) instanceof Number) || !(list.get(1) instanceof Number)) {
                    throw new IllegalArgumentException("WORD_COUNT_BETWEEN requires numeric values");
                }
                
                // Map field name (handle WORDCOUNT → wordCount)
                String fieldName = definition.ref().name().equals("WORDCOUNT") 
                    ? "wordCount" 
                    : definition.ref().name().toLowerCase();
                
                Integer min = ((Number) list.get(0)).intValue();
                Integer max = ((Number) list.get(1)).intValue();

                return cb.between(root.get(fieldName), min, max);
            };
        }
    }

    // ============================================================================
    // Test Data Setup
    // ============================================================================

    private static void createDocument(EntityManager em, String title, String content, int wordCount) {
        Document doc = new Document();
        doc.setTitle(title);
        doc.setContent(content);
        doc.setWordCount(wordCount);
        em.persist(doc);
    }

    // ============================================================================
    // Test Class: Registration and Lifecycle
    // ============================================================================

    @Nested
    @DisplayName("Registration and Lifecycle")
    class RegistrationAndLifecycle {

        @Test
        @DisplayName("Should register custom operator successfully")
        void shouldRegisterCustomOperator() {
            // Given
            CustomOperatorProvider provider = new StartsWithOperatorProvider();

            // When
            OperatorProviderRegistry.register(provider);

            // Then
            assertTrue(OperatorProviderRegistry.getProvider("STARTS_WITH").isPresent());
            assertEquals(provider, OperatorProviderRegistry.getProvider("STARTS_WITH").get());
        }

        @Test
        @DisplayName("Should unregister custom operator by provider")
        void shouldUnregisterCustomOperatorByProvider() {
            // Given
            CustomOperatorProvider provider = new StartsWithOperatorProvider();
            OperatorProviderRegistry.register(provider);

            // When
            OperatorProviderRegistry.unregister(provider);

            // Then
            assertFalse(OperatorProviderRegistry.getProvider("STARTS_WITH").isPresent());
        }

        @Test
        @DisplayName("Should unregister custom operators by code set")
        void shouldUnregisterCustomOperatorsByCodeSet() {
            // Given
            CustomOperatorProvider provider = new StartsWithOperatorProvider();
            OperatorProviderRegistry.register(provider);

            // When
            OperatorProviderRegistry.unregister(Set.of("STARTS_WITH"));

            // Then
            assertFalse(OperatorProviderRegistry.getProvider("STARTS_WITH").isPresent());
        }

        @Test
        @DisplayName("Should unregister all custom operators")
        void shouldUnregisterAllCustomOperators() {
            // Given
            OperatorProviderRegistry.register(new StartsWithOperatorProvider());
            OperatorProviderRegistry.register(new WordCountBetweenOperatorProvider());

            // When
            OperatorProviderRegistry.unregisterAll();

            // Then
            assertFalse(OperatorProviderRegistry.getProvider("STARTS_WITH").isPresent());
            assertFalse(OperatorProviderRegistry.getProvider("WORD_COUNT_BETWEEN").isPresent());
        }

        @Test
        @DisplayName("Should register provider supporting multiple operators")
        void shouldRegisterProviderSupportingMultipleOperators() {
            // Given
            CustomOperatorProvider multiProvider = new CustomOperatorProvider() {
                @Override
                public Set<String> supportedOperators() {
                    return Set.of("OP1", "OP2", "OP3");
                }

                @Override
                public <P extends Enum<P> & PropertyReference>
                PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
                    return (root, query, cb) -> cb.conjunction();
                }
            };

            // When
            OperatorProviderRegistry.register(multiProvider);

            // Then
            assertTrue(OperatorProviderRegistry.getProvider("OP1").isPresent());
            assertTrue(OperatorProviderRegistry.getProvider("OP2").isPresent());
            assertTrue(OperatorProviderRegistry.getProvider("OP3").isPresent());

            // All should return the same provider
            assertEquals(multiProvider, OperatorProviderRegistry.getProvider("OP1").get());
            assertEquals(multiProvider, OperatorProviderRegistry.getProvider("OP2").get());
            assertEquals(multiProvider, OperatorProviderRegistry.getProvider("OP3").get());
        }

        @Test
        @DisplayName("Should throw exception when registering provider with null or empty operator codes")
        void shouldThrowExceptionForNullOrEmptyOperatorCodes() {
            // Given
            CustomOperatorProvider nullProvider = new CustomOperatorProvider() {
                @Override
                public Set<String> supportedOperators() {
                    return null;
                }

                @Override
                public <P extends Enum<P> & PropertyReference>
                PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
                    return null;
                }
            };

            CustomOperatorProvider emptyProvider = new CustomOperatorProvider() {
                @Override
                public Set<String> supportedOperators() {
                    return Set.of();
                }

                @Override
                public <P extends Enum<P> & PropertyReference>
                PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
                    return null;
                }
            };

            // When & Then - null supportedOperators throws NullPointerException
            assertThrows(NullPointerException.class, () -> {
                OperatorProviderRegistry.register(nullProvider);
            });

            // Empty supportedOperators is allowed (no operators to register)
            // This is not an error - it just registers nothing
            OperatorProviderRegistry.register(emptyProvider);
        }
    }

    // ============================================================================
    // Test Class: Custom Operator Execution
    // ============================================================================

    @Nested
    @DisplayName("Custom Operator Execution")
    class CustomOperatorExecution {

        @BeforeEach
        void setupOperators() {
            OperatorProviderRegistry.register(new StartsWithOperatorProvider());
            OperatorProviderRegistry.register(new WordCountBetweenOperatorProvider());
        }

        @Test
        @DisplayName("Should execute STARTS_WITH operator correctly")
        void shouldExecuteStartsWithOperator() {
            // Given
            FilterDefinition<DocumentProperty> filter =
                    new FilterDefinition<>(DocumentProperty.TITLE, "STARTS_WITH", "Java");

            FilterRequest<DocumentProperty> request = FilterRequest.<DocumentProperty>builder()
                    .filter("startsWithJava", filter)
                    .combineWith("startsWithJava")
                    .build();

            // When
            FilterQuery<Document> handler = FilterQueryFactory.of(context);
            PredicateResolver<Document> predicateResolver = handler.toResolver(request);

            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Document> criteriaQuery = cb.createQuery(Document.class);
            Root<Document> root = criteriaQuery.from(Document.class);

            criteriaQuery.where(predicateResolver.resolve(root, criteriaQuery, cb));
            List<Document> results = em.createQuery(criteriaQuery).getResultList();

            // Then - Should find "Java Programming Basics", "Java 17 Features", and "JavaScript Guide"
            assertEquals(3, results.size());
            assertTrue(results.stream().allMatch(doc -> doc.getTitle().startsWith("Java")));
        }

        @Test
        @DisplayName("Should execute WORD_COUNT_BETWEEN operator correctly")
        void shouldExecuteWordCountBetweenOperator() {
            // Given
            FilterDefinition<DocumentProperty> filter =
                    new FilterDefinition<>(DocumentProperty.WORDCOUNT, "WORD_COUNT_BETWEEN", List.of(200, 400));

            FilterRequest<DocumentProperty> request = FilterRequest.<DocumentProperty>builder()
                    .filter("mediumDocs", filter)
                    .combineWith("mediumDocs")
                    .build();

            // When
            FilterQuery<Document> handler = FilterQueryFactory.of(context);
            PredicateResolver<Document> predicateResolver = handler.toResolver(request);

            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Document> criteriaQuery = cb.createQuery(Document.class);
            Root<Document> root = criteriaQuery.from(Document.class);

            criteriaQuery.where(predicateResolver.resolve(root, criteriaQuery, cb));
            List<Document> results = em.createQuery(criteriaQuery).getResultList();

            // Then
            assertEquals(3, results.size()); // 250, 300, 200
            assertTrue(results.stream().allMatch(doc ->
                    doc.getWordCount() >= 200 && doc.getWordCount() <= 400
            ));
        }

        @Test
        @DisplayName("Should combine custom operator with standard operators")
        void shouldCombineCustomWithStandardOperators() {
            // Given
            FilterDefinition<DocumentProperty> startsWithFilter =
                    new FilterDefinition<>(DocumentProperty.TITLE, "STARTS_WITH", "Java");

            FilterDefinition<DocumentProperty> wordCountFilter =
                    new FilterDefinition<>(DocumentProperty.WORDCOUNT, Op.GT, 200);

            FilterRequest<DocumentProperty> request = FilterRequest.<DocumentProperty>builder()
                    .filter("javaTitle", startsWithFilter)
                    .filter("longDoc", wordCountFilter)
                    .combineWith("javaTitle & longDoc")
                    .build();

            // When
            FilterQuery<Document> handler = FilterQueryFactory.of(context);
            PredicateResolver<Document> predicateResolver = handler.toResolver(request);

            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Document> criteriaQuery = cb.createQuery(Document.class);
            Root<Document> root = criteriaQuery.from(Document.class);

            criteriaQuery.where(predicateResolver.resolve(root, criteriaQuery, cb));
            List<Document> results = em.createQuery(criteriaQuery).getResultList();

            // Then - "Java 17 Features" (250) and "JavaScript Guide" (300) both match
            assertEquals(2, results.size());
            results.forEach(doc -> {
                assertTrue(doc.getTitle().startsWith("Java"));
                assertTrue(doc.getWordCount() > 200);
            });
        }

        @Test
        @DisplayName("Should combine multiple custom operators with OR logic")
        void shouldCombineMultipleCustomOperatorsWithOr() {
            // Given
            FilterDefinition<DocumentProperty> startsWithJava =
                    new FilterDefinition<>(DocumentProperty.TITLE, "STARTS_WITH", "Java");

            FilterDefinition<DocumentProperty> startsWithPython =
                    new FilterDefinition<>(DocumentProperty.TITLE, "STARTS_WITH", "Python");

            FilterRequest<DocumentProperty> request = FilterRequest.<DocumentProperty>builder()
                    .filter("java", startsWithJava)
                    .filter("python", startsWithPython)
                    .combineWith("java | python")
                    .build();

            // When
            FilterQuery<Document> handler = FilterQueryFactory.of(context);
            PredicateResolver<Document> predicateResolver = handler.toResolver(request);

            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Document> criteriaQuery = cb.createQuery(Document.class);
            Root<Document> root = criteriaQuery.from(Document.class);

            criteriaQuery.where(predicateResolver.resolve(root, criteriaQuery, cb));
            List<Document> results = em.createQuery(criteriaQuery).getResultList();

            // Then - 3 Java* + 1 Python = 4 total
            assertEquals(4, results.size());
            assertTrue(results.stream().allMatch(doc ->
                    doc.getTitle().startsWith("Java") || doc.getTitle().startsWith("Python")
            ));
        }
    }

    // ============================================================================
    // Test Class: Error Handling
    // ============================================================================

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should handle faulty custom operator provider gracefully")
        void shouldHandleFaultyProviderGracefully() {
            // Given - Provider that throws exception in toResolver
            CustomOperatorProvider faultyProvider = new CustomOperatorProvider() {
                @Override
                public Set<String> supportedOperators() {
                    return Set.of("FAULTY_OP");
                }

                @Override
                public <P extends Enum<P> & PropertyReference>
                PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
                    return (root, query, cb) -> {
                        throw new RuntimeException("Faulty operator implementation");
                    };
                }
            };

            OperatorProviderRegistry.register(faultyProvider);

            FilterDefinition<DocumentProperty> filter =
                    new FilterDefinition<>(DocumentProperty.TITLE, "FAULTY_OP", "test");

            FilterRequest<DocumentProperty> request = FilterRequest.<DocumentProperty>builder()
                    .filter("faulty", filter)
                    .combineWith("faulty")
                    .build();

            // When
            FilterQuery<Document> handler = FilterQueryFactory.of(context);
            PredicateResolver<Document> predicateResolver = handler.toResolver(request);

            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Document> criteriaQuery = cb.createQuery(Document.class);
            Root<Document> root = criteriaQuery.from(Document.class);

            // Then - Should throw exception during resolve
            assertThrows(RuntimeException.class, () -> {
                predicateResolver.resolve(root, criteriaQuery, cb);
            });
        }
    }

    // ============================================================================
    // Test Entity and Property Reference
    // ============================================================================


    enum DocumentProperty implements PropertyReference {
        TITLE,
        CONTENT,
        WORDCOUNT;

        @Override
        public Class<?> getType() {
            return switch (this){
                case TITLE -> String.class;
                case CONTENT -> String.class;
                case WORDCOUNT -> Integer.class;
            };
        }

        @Override
        public Set<Op> getSupportedOperators() {
            return switch (this){
                case TITLE -> Set.of(Op.EQ, Op.MATCHES);
                case CONTENT -> Set.of(Op.EQ, Op.MATCHES);
                case WORDCOUNT -> Set.of(Op.EQ, Op.GT, Op.GTE, Op.LT, Op.LTE, Op.RANGE);
            };
        }

        @Override
        public Class<?> getEntityType() {
            return Document.class;
        }
    }
}

