package io.github.cyfko.filterql.jpa;

import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.core.utils.ClassUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

public abstract class Repository<E> {
    protected final Class<E> entityClass;

    @SuppressWarnings("unchecked")
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

    List<E> findAll(PredicateResolver<E> resolver){
        EntityManager em = getEntityManager();

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<E> query = cb.createQuery(entityClass);
        Root<E> root = query.from(entityClass);

        // appliquer les filtres
        query.select(root).where(resolver.resolve(root, query, cb));

        return em.createQuery(query).getResultList();
    }

    void deleteAll(){
        EntityManager em = getEntityManager();

        try {
            em.getTransaction().begin();

            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaDelete<E> delete = cb.createCriteriaDelete(entityClass);

            int deleted = em.createQuery(delete).executeUpdate();
            System.out.println("Deleted " + deleted + " rows");

            em.getTransaction().commit();

        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        }
    }
}
