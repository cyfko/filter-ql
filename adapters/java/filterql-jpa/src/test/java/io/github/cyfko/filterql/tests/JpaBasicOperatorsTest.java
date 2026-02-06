package io.github.cyfko.filterql.tests;

import io.github.cyfko.filterql.core.api.Condition;
import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.core.api.PropertyReference;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import io.github.cyfko.filterql.tests.entities.basic_operators.ProductB;
import jakarta.persistence.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests simplifiés pour démontrer les opérateurs JPA FilterQL.
 * Utilise une approche linéaire pour éviter les problèmes d'isolation de données.
 */
@DisplayName("JPA Basic Operators Test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JpaBasicOperatorsTest {

    private static EntityManagerFactory emf;

    enum ProductProp implements PropertyReference {
        NAME,
        PRICE;

        @Override
        public Class<?> getType() {
            return switch (this){
                case NAME -> String.class;
                case PRICE -> Integer.class;
            };
        }

        @Override
        public Set<Op> getSupportedOperators() {
            return switch (this){
                case NAME -> Set.of(Op.EQ, Op.NE, Op.MATCHES, Op.NOT_MATCHES, Op.IN, Op.NOT_IN, Op.IS_NULL, Op.NOT_NULL);
                case PRICE -> Set.of(Op.EQ, Op.NE, Op.GT, Op.GTE, Op.LT, Op.LTE, Op.IN, Op.NOT_IN, Op.RANGE, Op.NOT_RANGE, Op.IS_NULL, Op.NOT_NULL);
            };
        }

        @Override
        public Class<?> getEntityType() {
            return ProductB.class;
        }
    }

    @BeforeAll
    static void setup() {
        emf = Persistence.createEntityManagerFactory("testPU");
        
        // Insert initial test data once
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.persist(new ProductB("Laptop", 1200));
        em.persist(new ProductB("Mouse", 25));
        em.persist(new ProductB("Keyboard", 75));
        em.persist(new ProductB("Monitor", 350));
        em.persist(new ProductB("Headset", 120));
        
        // Add product with null price
        ProductB nullProduct = new ProductB("NullPrice", null);
        em.persist(nullProduct);
        
        em.getTransaction().commit();
        em.close();
    }

    @AfterAll
    static void teardown() {
        if (emf != null) emf.close();
    }

    private JpaFilterContext<ProductProp> createContext() {
        return new JpaFilterContext<>(
            ProductProp.class,
            ref -> switch (ref) {
                case NAME -> "name";
                case PRICE -> "price";
            }
        );
    }

    private List<ProductB> executeQuery(PredicateResolver<?> resolver) {
        try (EntityManager em = emf.createEntityManager()) {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<ProductB> query = cb.createQuery(ProductB.class);
            Root<ProductB> root = query.from(ProductB.class);

            //noinspection rawtypes,unchecked
            query.where(resolver.resolve((Root) root, query, cb));

            return em.createQuery(query).getResultList();
        }
    }

    @Test
    @Order(1)
    @DisplayName("EQ operator should match exact values")
    void testEqOperator() {
        JpaFilterContext<ProductProp> context = createContext();
        Condition condition = context.toCondition("nameParam", ProductProp.NAME, "EQ");
        
        Map<String, Object> args = Map.of("nameParam", "Laptop");
        PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));
        
        List<ProductB> results = executeQuery(resolver);
        
        assertEquals(1, results.size());
        assertEquals("Laptop", results.getFirst().getName());
    }

    @Test
    @Order(2)
    @DisplayName("NE operator should exclude matching values")
    void testNeOperator() {
        JpaFilterContext<ProductProp> context = createContext();
        Condition condition = context.toCondition("nameParam", ProductProp.NAME, "NE");
        
        Map<String, Object> args = Map.of("nameParam", "Laptop");
        PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));
        
        List<ProductB> results = executeQuery(resolver);
        
        assertEquals(5, results.size()); // All except Laptop
        assertTrue(results.stream().noneMatch(p -> "Laptop".equals(p.getName())));
    }

    @Test
    @Order(3)
    @DisplayName("GT operator should match values greater than")
    void testGtOperator() {
        JpaFilterContext<ProductProp> context = createContext();
        Condition condition = context.toCondition("priceParam", ProductProp.PRICE, "GT");
        
        Map<String, Object> args = Map.of("priceParam", 100);
        PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));
        
        List<ProductB> results = executeQuery(resolver);
        
        assertEquals(3, results.size()); // Laptop (1200), Monitor (350), Headset (120)
        assertTrue(results.stream().allMatch(p -> p.getPrice() != null && p.getPrice() > 100));
    }

    @Test
    @Order(4)
    @DisplayName("LT operator should match values less than")
    void testLtOperator() {
        JpaFilterContext<ProductProp> context = createContext();
        Condition condition = context.toCondition("priceParam", ProductProp.PRICE, "LT");
        
        Map<String, Object> args = Map.of("priceParam", 100);
        PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));
        
        List<ProductB> results = executeQuery(resolver);
        
        assertEquals(2, results.size()); // Mouse (25), Keyboard (75)
        assertTrue(results.stream().allMatch(p -> p.getPrice() != null && p.getPrice() < 100));
    }

    @Test
    @Order(5)
    @DisplayName("IN operator should match values in collection")
    void testInOperator() {
        JpaFilterContext<ProductProp> context = createContext();
        Condition condition = context.toCondition("namesParam", ProductProp.NAME, "IN");
        
        Map<String, Object> args = Map.of("namesParam", List.of("Laptop", "Mouse", "Tablet"));
        PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));
        
        List<ProductB> results = executeQuery(resolver);
        
        assertEquals(2, results.size()); // Laptop, Mouse (Tablet doesn't exist)
        assertTrue(results.stream().allMatch(p -> 
            List.of("Laptop", "Mouse").contains(p.getName())
        ));
    }

    @Test
    @Order(6)
    @DisplayName("NOT_IN operator should exclude values in collection")
    void testNotInOperator() {
        JpaFilterContext<ProductProp> context = createContext();
        Condition condition = context.toCondition("pricesParam", ProductProp.PRICE, "NOT_IN");
        
        Map<String, Object> args = Map.of("pricesParam", List.of(25, 75));
        PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));
        
        List<ProductB> results = executeQuery(resolver);
        
        assertEquals(3, results.size()); // Laptop, Monitor, Headset (null price excluded automatically)
        assertTrue(results.stream().noneMatch(p -> 
            p.getPrice() == null || List.of(25, 75).contains(p.getPrice())
        ));
    }

    @Test
    @Order(7)
    @DisplayName("RANGE operator should match values within range (BETWEEN)")
    void testRangeOperator() {
        JpaFilterContext<ProductProp> context = createContext();
        Condition condition = context.toCondition("priceParam", ProductProp.PRICE, "RANGE");
        
        Map<String, Object> args = new HashMap<>();
        args.put("priceParam", new Object[]{50, 200});
        PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));
        
        List<ProductB> results = executeQuery(resolver);
        
        assertEquals(2, results.size()); // Keyboard (75), Headset (120)
        assertTrue(results.stream().allMatch(p -> 
            p.getPrice() != null && p.getPrice() >= 50 && p.getPrice() <= 200
        ));
    }

    @Test
    @Order(8)
    @DisplayName("MATCHES operator should support SQL LIKE patterns")
    void testMatchesOperator() {
        JpaFilterContext<ProductProp> context = createContext();
        Condition condition = context.toCondition("nameParam", ProductProp.NAME, "MATCHES");
        
        Map<String, Object> args = Map.of("nameParam", "%top");
        PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));
        
        List<ProductB> results = executeQuery(resolver);
        
        assertEquals(1, results.size());
        assertEquals("Laptop", results.getFirst().getName());
    }

    @Test
    @Order(9)
    @DisplayName("IS_NULL operator should match null values")
    void testIsNullOperator() {
        JpaFilterContext<ProductProp> context = createContext();
        Condition condition = context.toCondition("priceParam", ProductProp.PRICE, "IS_NULL");
        
        Map<String, Object> args = new HashMap<>();
        args.put("priceParam", null); // IS_NULL accepts null
        PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));
        
        List<ProductB> results = executeQuery(resolver);
        
        assertEquals(1, results.size());
        assertEquals("NullPrice", results.getFirst().getName());
        assertNull(results.getFirst().getPrice());
    }

    @Test
    @Order(10)
    @DisplayName("NOT_NULL operator should match non-null values")
    void testNotNullOperator() {
        JpaFilterContext<ProductProp> context = createContext();
        Condition condition = context.toCondition("priceParam", ProductProp.PRICE, "NOT_NULL");
        
        Map<String, Object> args = new HashMap<>();
        args.put("priceParam", null); // NOT_NULL also accepts null
        PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));
        
        List<ProductB> results = executeQuery(resolver);
        
        assertEquals(5, results.size()); // All except NullPrice
        assertTrue(results.stream().allMatch(p -> p.getPrice() != null));
    }

    @Test
    @Order(11)
    @DisplayName("AND combination should match both conditions")
    void testAndCombination() {
        JpaFilterContext<ProductProp> context = createContext();
        
        // price > 50 AND price < 200
        Condition gtCondition = context.toCondition("minParam", ProductProp.PRICE, "GT");
        Condition ltCondition = context.toCondition("maxParam", ProductProp.PRICE, "LT");
        Condition combined = gtCondition.and(ltCondition);
        
        Map<String, Object> args = new HashMap<>();
        args.put("minParam", 50);
        args.put("maxParam", 200);
        PredicateResolver<?> resolver = context.toResolver(combined, QueryExecutionParams.of(args));
        
        List<ProductB> results = executeQuery(resolver);
        
        assertEquals(2, results.size()); // Keyboard (75), Headset (120)
        assertTrue(results.stream().allMatch(p -> 
            p.getPrice() != null && p.getPrice() > 50 && p.getPrice() < 200
        ));
    }

    @Test
    @Order(12)
    @DisplayName("OR combination should match either condition")
    void testOrCombination() {
        JpaFilterContext<ProductProp> context = createContext();
        
        // price < 30 OR price > 300
        Condition lowCondition = context.toCondition("lowParam", ProductProp.PRICE, "LT");
        Condition highCondition = context.toCondition("highParam", ProductProp.PRICE, "GT");
        Condition combined = lowCondition.or(highCondition);
        
        Map<String, Object> args = new HashMap<>();
        args.put("lowParam", 30);
        args.put("highParam", 300);
        PredicateResolver<?> resolver = context.toResolver(combined, QueryExecutionParams.of(args));
        
        List<ProductB> results = executeQuery(resolver);
        
        assertEquals(3, results.size()); // Mouse (25), Laptop (1200), Monitor (350)
    }

    @Test
    @Order(13)
    @DisplayName("NOT negation should invert condition")
    void testNotNegation() {
        JpaFilterContext<ProductProp> context = createContext();
        
        // NOT (price < 100) equivalent to price >= 100
        Condition ltCondition = context.toCondition("priceParam", ProductProp.PRICE, "LT");
        Condition negated = ltCondition.not();
        
        Map<String, Object> args = Map.of("priceParam", 100);
        PredicateResolver<?> resolver = context.toResolver(negated, QueryExecutionParams.of(args));
        
        List<ProductB> results = executeQuery(resolver);
        
        // Should return: Headset (120), Monitor (350), Laptop (1200)
        // Note: null price also excluded by NOT (< 100)
        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(p -> 
            p.getPrice() != null && p.getPrice() >= 100
        ));
    }

    @Test
    @Order(14)
    @DisplayName("GTE (Greater Than or Equal) should match >= value")
    void testGte() {
        JpaFilterContext<ProductProp> context = createContext();
        
        Condition condition = context.toCondition("priceParam", ProductProp.PRICE, "GTE");
        
        Map<String, Object> args = Map.of("priceParam", 120);
        PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));
        
        List<ProductB> results = executeQuery(resolver);
        
        // Should return: Headset (120), Monitor (350), Laptop (1200)
        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(p -> p.getPrice() >= 120));
    }

    @Test
    @Order(15)
    @DisplayName("LTE (Less Than or Equal) should match <= value")
    void testLte() {
        JpaFilterContext<ProductProp> context = createContext();
        
        Condition condition = context.toCondition("priceParam", ProductProp.PRICE, "LTE");
        
        Map<String, Object> args = Map.of("priceParam", 120);
        PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));
        
        List<ProductB> results = executeQuery(resolver);
        
        // Should return: Mouse (25), Keyboard (45), Headset (120)
        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(p -> p.getPrice() <= 120));
    }

    @Test
    @Order(16)
    @DisplayName("NOT_MATCHES should exclude matching pattern")
    void testNotMatches() {
        JpaFilterContext<ProductProp> context = createContext();
        
        Condition condition = context.toCondition("nameParam", ProductProp.NAME, "NOT_MATCHES");
        
        Map<String, Object> args = Map.of("nameParam", "%top%");
        PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));
        
        List<ProductB> results = executeQuery(resolver);
        
        // Should return all EXCEPT Laptop & Desktop (5 items)
        assertEquals(5, results.size());
        assertTrue(results.stream().noneMatch(p -> 
            p.getName().toLowerCase().contains("top")
        ));
    }

    @Test
    @Order(17)
    @DisplayName("NOT_RANGE should exclude values in range")
    void testNotRange() {
        JpaFilterContext<ProductProp> context = createContext();
        
        Condition condition = context.toCondition("priceParam", ProductProp.PRICE, "NOT_RANGE");
        
        Map<String, Object> args = Map.of("priceParam", List.of(50, 300));
        PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));
        
        List<ProductB> results = executeQuery(resolver);
        
        // Should return: Mouse (25), Keyboard (45), Laptop (1200)
        // Excludes: Headset (120 in range), Monitor (350 out but close), Desktop (null handled by range logic)
        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(p -> 
            p.getPrice() != null && (p.getPrice() < 50 || p.getPrice() > 300)
        ));
    }
}
