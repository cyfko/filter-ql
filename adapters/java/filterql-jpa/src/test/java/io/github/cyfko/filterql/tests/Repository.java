package io.github.cyfko.filterql.tests;

import io.github.cyfko.filterql.core.spi.ConditionResolver;
import io.github.cyfko.filterql.jpa.spi.ManagerDetail;
import io.github.cyfko.filterql.jpa.spi.PredicateResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

public abstract class Repository<E> {
    protected final Class<E> entityClass;

    protected Repository() {
        this.entityClass = getGenericParameterClass(getClass(), 0);
    }

    private static <T> Class<T> getGenericParameterClass(Class<?> clazz, int index) {
        Type superclass = clazz.getGenericSuperclass();
        if (superclass instanceof ParameterizedType) {
            Type[] types = ((ParameterizedType) superclass).getActualTypeArguments();
            if (types[index] instanceof Class<?>) {
                return (Class<T>) types[index];
            }
        }
        throw new IllegalArgumentException("Impossible de déterminer le type générique de " + clazz);
    }

    public abstract EntityManager getEntityManager();

    List<E> findAll(ConditionResolver<EntityManager, ManagerDetail> cr) {
        EntityManager em = getEntityManager();

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<E> query = cb.createQuery(entityClass);
        Root<E> root = query.from(entityClass);

        // Extract the JpaPredicateResolver from the ConditionResolver
        if (!(cr instanceof PredicateResolver<?> pr))
            throw new IllegalArgumentException("Expected JpaConditionResolver, got: " + cr.getClass());

        // Apply filters
        //noinspection unchecked
        query.select(root).where(pr.resolve((Root) root, query, cb));

        return em.createQuery(query).getResultList();
    }

    void deleteAll() {
        EntityManager em = getEntityManager();

        try {
            em.getTransaction().begin();

            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaDelete<E> delete = cb.createCriteriaDelete(entityClass);

            int deleted = em.createQuery(delete).executeUpdate();
            System.out.println("Deleted " + deleted + " rows");

            em.getTransaction().commit();

        } catch (Exception e) {
            if (em.getTransaction().isActive())
                em.getTransaction().rollback();
            throw e;
        }
    }
}
