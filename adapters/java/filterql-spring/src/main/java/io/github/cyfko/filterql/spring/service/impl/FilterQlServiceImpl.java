package io.github.cyfko.filterql.spring.service.impl;

import io.github.cyfko.filterql.core.FilterQueryFactory;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.validation.PropertyReference;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import io.github.cyfko.filterql.jpa.projection.InstanceResolver;
import io.github.cyfko.filterql.jpa.strategies.CountStrategy;
import io.github.cyfko.filterql.jpa.strategies.MultiQueryFetchStrategy;
import io.github.cyfko.filterql.jpa.strategies.TypedMultiQueryFetchStrategy;
import io.github.cyfko.filterql.spring.pagination.ResultMapper;
import io.github.cyfko.filterql.spring.pagination.PaginatedData;
import io.github.cyfko.filterql.spring.pagination.PaginationInfo;
import io.github.cyfko.filterql.spring.service.FilterQlService;
import io.github.cyfko.filterql.spring.support.FilterContextRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class FilterQlServiceImpl implements FilterQlService {
    @PersistenceContext
    private EntityManager em;

    private final FilterContextRegistry contextRegistry;
    private final InstanceResolver instanceResolver;
    
    public FilterQlServiceImpl(FilterContextRegistry contextRegistry, InstanceResolver instanceResolver) {
        this.contextRegistry = contextRegistry;
        this.instanceResolver = instanceResolver;
    }

    @Override
    public <P extends Enum<P> & PropertyReference>
    PaginatedData<Map<String,Object>> search(Class<P> refClass, FilterRequest<P> filterRequest) {
        // 0. Determiner la classe de projection utilisée.
        Class<?> projectionClass = toProjectionClass(refClass);

        // 1. Récupérer le JpaFilterContext généré pour cette entité
        JpaFilterContext<?> context = contextRegistry.getContext(refClass);

        // 2. Exécuter avec le repository
        MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(projectionClass, instanceResolver);
        List<Map<String, Object>> results = FilterQueryFactory.of(context).execute(filterRequest, em, strategy);

        // 3. Count all matchs
        Long counted = FilterQueryFactory.of(context).execute(filterRequest, em, new CountStrategy(projectionClass));

        // 3. Generate pagination info
        int page = filterRequest.hasPagination() ? filterRequest.pagination().page() : 0;
        PaginationInfo pagination = new PaginationInfo(page, results.size(), counted);

        // Ok
        return new PaginatedData<>(results, pagination);
    }

    @Override
    public <R, P extends Enum<P> & PropertyReference>
    PaginatedData<R> search(Class<R> projectionClass, FilterRequest<P> filterRequest, ResultMapper<R> resultMapper) {
        PaginatedData<Map<String, Object>> paginatedData = search(toEnumClass(projectionClass), filterRequest);
        return paginatedData.map(resultMapper::map);
    }

    private static <P extends Enum<P> & PropertyReference> Class<?> toProjectionClass(Class<P> refClass){
        String fqcn = refClass.getCanonicalName();
        try {
            return Class.forName(fqcn.substring(0, fqcn.lastIndexOf('_')));
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Expected reference class has to be a Filter QL generated enum class: " + fqcn, e);
        }
    }

    private static <P extends Enum<P> & PropertyReference> Class<P> toEnumClass(Class<?> projectionClass){
        try {
            //noinspection unchecked
            return (Class<P>) Class.forName(projectionClass.getCanonicalName() +  "_");
        } catch (ClassNotFoundException | ClassCastException e) {
            throw new IllegalArgumentException("Expected a projection class. found: " + projectionClass.getCanonicalName(), e);
        }
    }
}