package io.github.cyfko.filterql.spring.service.impl;

import io.github.cyfko.filterql.core.FilterQueryFactory;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.api.PropertyReference;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import io.github.cyfko.filterql.jpa.spi.InstanceResolver;
import io.github.cyfko.filterql.jpa.strategies.CountStrategy;
import io.github.cyfko.filterql.jpa.strategies.MultiQueryFetchStrategy;
import io.github.cyfko.filterql.jpa.strategies.helper.RowBuffer;
import io.github.cyfko.filterql.spring.pagination.ResultMapper;
import io.github.cyfko.filterql.spring.pagination.PaginatedData;
import io.github.cyfko.filterql.spring.pagination.PaginationInfo;
import io.github.cyfko.filterql.spring.projection.ProjectionProxyFactory;
import io.github.cyfko.filterql.spring.service.FilterQlService;
import io.github.cyfko.filterql.spring.support.FilterContextRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Default JPA-based implementation of {@link FilterQlService}.
 * <p>
 * This service is auto-registered as a Spring {@code @Service} bean and
 * provides
 * three query methods:
 * </p>
 * <ul>
 * <li>{@link #search(Class, FilterRequest)} — returns raw
 * {@code Map<String, Object>} results</li>
 * <li>{@link #search(Class, FilterRequest, ResultMapper)} — returns
 * custom-mapped DTOs</li>
 * <li>{@link #searchAs(Class, FilterRequest)} — returns JDK proxy
 * implementations
 * of the given projection interface, created via
 * {@link ProjectionProxyFactory}</li>
 * </ul>
 *
 * <p>
 * <strong>Enum ↔ Projection class convention:</strong> FilterQL relies on a
 * naming
 * convention where the generated PropertyRef enum is named
 * {@code {ProjectionClass}_}
 * (e.g., {@code UserDTO_} for {@code UserDTO}). The private helper methods
 * {@code toProjectionClass} and {@code toEnumClass} use this convention to
 * navigate
 * between the two.
 * </p>
 *
 * <p>
 * <strong>Thread safety:</strong> This bean is a Spring singleton. The
 * {@link jakarta.persistence.EntityManager} is injected via
 * {@code @PersistenceContext},
 * which provides a thread-bound proxy — safe for concurrent use.
 * </p>
 *
 * @see FilterQlService
 * @see ProjectionProxyFactory
 * @see FilterContextRegistry
 * @author Frank KOSSI
 * @since 4.0.0
 */
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
    public <P extends Enum<P> & PropertyReference> PaginatedData<Map<String, Object>> search(Class<P> refClass,
            FilterRequest<P> filterRequest) {
        // 0. Determiner la classe de projection utilisée.
        Class<?> projectionClass = toProjectionClass(refClass);

        // 1. Récupérer le JpaFilterContext généré pour cette entité
        JpaFilterContext<?> context = contextRegistry.getContext(refClass);

        // 2. Exécuter avec le repository
        MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(projectionClass, instanceResolver);
        List<RowBuffer> results = FilterQueryFactory.of(context).execute(filterRequest, em, strategy);

        // 3. Count all matchs
        Long counted = FilterQueryFactory.of(context).execute(filterRequest, em, new CountStrategy(projectionClass));

        // 3. Generate pagination info
        int page = filterRequest.hasPagination() ? filterRequest.pagination().page() : 0;
        PaginationInfo pagination = new PaginationInfo(page, results.size(), counted);

        // Ok
        return new PaginatedData<>(results.stream().map(RowBuffer::toMap), pagination);
    }

    @Override
    public <R, P extends Enum<P> & PropertyReference> PaginatedData<R> search(Class<R> projectionClass,
            FilterRequest<P> filterRequest, ResultMapper<R> resultMapper) {
        PaginatedData<Map<String, Object>> paginatedData = search(toEnumClass(projectionClass), filterRequest);
        return paginatedData.map(resultMapper::map);
    }

    @Override
    public <T, P extends Enum<P> & PropertyReference> PaginatedData<T> searchAs(Class<T> projectionInterface,
            FilterRequest<P> filterRequest) {
        PaginatedData<Map<String, Object>> raw = search(toEnumClass(projectionInterface), filterRequest);
        return raw.map(map -> ProjectionProxyFactory.create(projectionInterface, map));
    }

    private static <P extends Enum<P> & PropertyReference> Class<?> toProjectionClass(Class<P> refClass) {
        String fqcn = refClass.getCanonicalName();
        try {
            return Class.forName(fqcn.substring(0, fqcn.lastIndexOf('_')));
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(
                    "Expected reference class has to be a Filter QL generated enum class: " + fqcn, e);
        }
    }

    private static <P extends Enum<P> & PropertyReference> Class<P> toEnumClass(Class<?> projectionClass) {
        try {
            // noinspection unchecked
            return (Class<P>) Class.forName(projectionClass.getCanonicalName() + "_");
        } catch (ClassNotFoundException | ClassCastException e) {
            throw new IllegalArgumentException(
                    "Expected a projection class. found: " + projectionClass.getCanonicalName(), e);
        }
    }
}