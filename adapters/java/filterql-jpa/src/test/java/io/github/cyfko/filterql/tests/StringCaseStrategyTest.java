package io.github.cyfko.filterql.tests;

import io.github.cyfko.filterql.core.api.Condition;
import io.github.cyfko.filterql.core.config.FilterConfig;
import io.github.cyfko.filterql.core.config.StringCaseStrategy;
import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.core.api.PropertyReference;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StringCaseStrategy} behavior in the deferred arguments architecture.
 * Validates that case conversion strategies are properly applied during query execution.
 */
@Transactional
class StringCaseStrategyTest {

    private static EntityManagerFactory emf;
    private EntityManager em;

    @BeforeAll
    static void init() {
        emf = Persistence.createEntityManagerFactory("testPU");
    }

    @BeforeEach
    void setUp() {
        em = emf.createEntityManager();
        em.getTransaction().begin();
    }

    @AfterEach
    void tearDown() {
        em.getTransaction().commit();
        if (em.isOpen()) em.close();
    }

    @AfterAll
    static void close() {
        if (emf.isOpen()) emf.close();
    }

    enum Ref implements PropertyReference {
        NAME;

        @Override
        public Class<?> getType(){
            return switch (this){
                case NAME -> String.class;
            };
        }

        @Override
        public Set<Op> getSupportedOperators(){
            return switch (this) {
                case NAME -> Set.of(Op.MATCHES);
            };
        }

        @Override
        public Class<?> getEntityType() {
            return SimpleUser.class;
        }
    }

    private JpaFilterContext<Ref> ctx(StringCaseStrategy strat) {
        return new JpaFilterContext<>(
            Ref.class,
            ref -> "name",
            FilterConfig.builder().stringCaseStrategy(strat).build()
        );
    }

    @Test
    @DisplayName("LOWER strategy enables case-insensitive MATCHES while NONE remains case-sensitive")
    void lowerStrategyMatchesWhereNoneFails() {
        SimpleUser u = new SimpleUser("abcvalue", 30, "a@b.c");
        em.persist(u);
        em.flush();

        String pattern = "%ABCVAL%"; // Upper-case pattern

        // Create conditions with same structure
        var lowerCtx = ctx(StringCaseStrategy.LOWER);
        var noneCtx = ctx(StringCaseStrategy.NONE);

        Condition lowerCondition = lowerCtx.toCondition("arg1", Ref.NAME, "MATCHES");
        Condition noneCondition = noneCtx.toCondition("arg1", Ref.NAME, "MATCHES");

        // Provide argument values
        Map<String, Object> args = Map.of("arg1", pattern);

        // Execute queries
        List<SimpleUser> lowerResult = run(lowerCtx.toResolver(lowerCondition, QueryExecutionParams.of(args)));
        List<SimpleUser> noneResult = run(noneCtx.toResolver(noneCondition, QueryExecutionParams.of(args)));

        assertEquals(1, lowerResult.size(), "LOWER strategy should find the row");
        assertEquals(0, noneResult.size(), "NONE strategy should be case-sensitive and miss the row");
    }

    private List<SimpleUser> run(PredicateResolver<?> resolver) {
        var cb = em.getCriteriaBuilder();
        var cq = cb.createQuery(SimpleUser.class);
        var root = cq.from(SimpleUser.class);
        //noinspection rawtypes,unchecked
        cq.select(root).where(resolver.resolve((Root) root, cq, cb));
        TypedQuery<SimpleUser> q = em.createQuery(cq);
        return q.getResultList();
    }
}
