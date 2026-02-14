package io.github.cyfko.filterql.spring;

import io.github.cyfko.projection.Method;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * REST API exposure options used in conjunction with {@link io.github.cyfko.projection.Projection} annotation.
 * When applied, this annotation implies generation of a search endpoint at {@code [base-path]/search/[resource-name-or-auto-generated-name]}
 * <p>
 * Allows configuring REST endpoint exposure, filtering strategies, and custom processing pipelines.
 * </p>
 * <p>If this annotation is used standalone (without {@link io.github.cyfko.projection.Projection}) it has no effect.</p>
 *
 * <h2>Configuration Options</h2>
 * <ul>
 *   <li><b>value</b>: name of the exposed REST resource in kebab-case. Defaults to kebab-case of the entity class name.</li>
 *   <li><b>basePath</b>: optional URI path prefix for REST endpoints; kebab-case recommended.</li>
 *   <li><b>strategy</b>: determines the endpoint return type and behavior (PAGINATED, LIST, COUNT, SINGLE, CUSTOM).</li>
 *   <li><b>pipes</b>: filter transformation pipeline applied before handler execution.</li>
 *   <li><b>handler</b>: custom endpoint implementation with full control over filtering and response.</li>
 *   <li><b>endpointName</b>: custom method name for the generated endpoint.</li>
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Exposure {

    /**
     * Name of the REST resource exposed (kebab-case).
     * Defaults to the kebab-case form of the entity class name.
     */
    String value() default "";

    /**
     * Optional URI path prefix for the REST endpoints.
     * Kebab-case is recommended.
     */
    String basePath() default "";

    /**
     * Strategy determining the endpoint return type and behavior.
     * <p>
     * Each strategy enforces specific handler method signatures:
     * <ul>
     *   <li>PAGINATED: {@code PaginatedData<T> method(FilterRequest)}</li>
     *   <li>LIST: {@code List<T> method(FilterRequest)}</li>
     *   <li>COUNT: {@code long method(FilterRequest)}</li>
     *   <li>SINGLE: {@code Optional<T> method(FilterRequest)}</li>
     *   <li>CUSTOM: User-defined signature</li>
     * </ul>
     */
    Strategy strategy() default Strategy.PROJECTED;

    /**
     * Pipeline of filter transformations applied before the handler execution.
     * <p>
     * Pipes allow you to intercept and modify the incoming {@code FilterRequest}
     * before it reaches the handler. Common use cases include:
     * </p>
     * <ul>
     *   <li>Adding tenant isolation filters</li>
     *   <li>Applying security-based filters</li>
     *   <li>Validating or sanitizing filter inputs</li>
     *   <li>Adding default sorting or pagination constraints</li>
     *   <li>Enriching filters with contextual data</li>
     * </ul>
     *
     * <h2>Execution Order</h2>
     * Pipes are executed in the order they are declared:
     * <pre>
     * FilterRequest → Pipe1 → Pipe2 → Pipe3 → Handler
     * </pre>
     *
     * <h2>Method Name Requirement</h2>
     * <p>
     * <strong>IMPORTANT:</strong> Unlike other uses of {@code @Method}, the {@code value}
     * attribute (method name) is <strong>REQUIRED</strong> for pipes, whether {@code type}
     * is specified or not.
     * </p>
     * <p>
     * <strong>Rationale:</strong> Pipes are generic transformations without inherent context
     * to derive a conventional name. Each pipe represents a specific transformation intent
     * (tenant filtering, sanitization, validation, etc.) that must be explicitly named.
     * </p>
     *
     * <pre>{@code
     * // ✅ CORRECT - Method name always specified
     * @Exposure(
     *   value = "users",
     *   pipes = {
     *     @Method("applyTenantFilter"),
     *     @Method(type = FilterPipes.class, value = "sanitize")
     *   }
     * )
     *
     * // ❌ INCORRECT - Missing method name will cause compilation error
     * @Exposure(
     *   value = "users",
     *   pipes = @Method()  // Error: method name required for pipes
     * )
     *
     * // ❌ INCORRECT - Type alone is not sufficient
     * @Exposure(
     *   value = "users",
     *   pipes = @Method(type = FilterPipes.class)  // Error: which method?
     * )
     * }</pre>
     *
     * <h2>Usage Examples</h2>
     *
     * <h3>Inline Pipes (Current Class)</h3>
     * <pre>{@code
     * @Exposure(
     *   value = "orders",
     *   pipes = {
     *     @Method("applyTenantFilter"),
     *     @Method("applySecurityFilter"),
     *     @Method("validateFilter")
     *   }
     * )
     * public class OrderDTO {
     *
     *   // Applies tenant isolation
     *   public static FilterRequest applyTenantFilter(FilterRequest filter) {
     *     return filter.and("tenantId", CurrentTenant.getId());
     *   }
     *
     *   // Applies security-based filtering
     *   public static FilterRequest applySecurityFilter(FilterRequest filter) {
     *     User user = SecurityContext.getCurrentUser();
     *     if (!user.isAdmin()) {
     *       return filter.and("customerId", user.getId());
     *     }
     *     return filter;
     *   }
     *
     *   // Validates and limits filter parameters
     *   public static FilterRequest validateFilter(FilterRequest filter) {
     *     if (filter.getLimit() > 1000) {
     *       filter.setLimit(1000);
     *     }
     *     return filter;
     *   }
     * }
     * }</pre>
     *
     * <h3>External Reusable Pipes</h3>
     * <pre>{@code
     * // Shared filter transformations
     * public class FilterPipes {
     *
     *   // Applies tenant isolation to the filter.
     *   // Can be used in any context where tenant filtering is needed.
     *   public static FilterRequest tenantIsolation(FilterRequest filter) {
     *     return filter.and("tenantId", SecurityContext.getTenantId());
     *   }
     *
     *   // Filters out soft-deleted entities
     *   public static FilterRequest softDeleteFilter(FilterRequest filter) {
     *     return filter.and("deleted", false);
     *   }
     *
     *   // Removes potentially dangerous or sensitive filters
     *   public static FilterRequest sanitizeInput(FilterRequest filter) {
     *     filter.removeFiltersOn("password", "internalId", "secret");
     *     return filter;
     *   }
     *
     *   // Logs filter access for auditing purposes
     *   public static FilterRequest auditLog(FilterRequest filter) {
     *     AuditService.log("Filter applied: " + filter);
     *     return filter;
     *   }
     * }
     *
     * // Usage - Method name ALWAYS required
     * @Exposure(
     *   value = "users",
     *   pipes = {
     *     @Method(type = FilterPipes.class, value = "tenantIsolation"),
     *     @Method(type = FilterPipes.class, value = "softDeleteFilter"),
     *     @Method(type = FilterPipes.class, value = "sanitizeInput")
     *   }
     * )
     * public class UserDTO { }
     * }</pre>
     *
     * <h3>Pipes with Dependency Injection</h3>
     * Pipes can request additional dependencies as parameters:
     * <pre>{@code
     * public static FilterRequest applySecurityFilter(
     *     FilterRequest filter,
     *     @AuthenticationPrincipal User currentUser,
     *     SecurityService securityService
     * ) {
     *     if (securityService.requiresFiltering(currentUser)) {
     *         return filter.and("ownerId", currentUser.getId());
     *     }
     *     return filter;
     * }
     * }</pre>
     *
     * <h3>Pipe Requirements</h3>
     * <ul>
     *   <li>Must be public static</li>
     *   <li>Must return {@code FilterRequest}</li>
     *   <li>First parameter must be {@code FilterRequest}</li>
     *   <li>Can declare additional parameters for dependency injection</li>
     *   <li>Should be pure functions when possible (no side effects preferred, except logging/auditing)</li>
     *   <li><strong>Method name must be explicitly specified in @Method annotation</strong></li>
     * </ul>
     *
     * <h3>Reusability Beyond @Exposure</h3>
     * <p>
     * Pipe methods are regular public static methods that can be invoked directly
     * from any part of your application:
     * </p>
     * <pre>{@code
     * // Direct usage in service layer
     * public class OrderService {
     *     public List<Order> getOrders(FilterRequest filter) {
     *         filter = FilterPipes.tenantIsolation(filter);
     *         filter = FilterPipes.softDeleteFilter(filter);
     *         return repository.findAll(filter.toSpecification());
     *     }
     * }
     *
     * // Composition in tests
     * @Test
     * void testSecureFiltering() {
     *     FilterRequest filter = new FilterRequest();
     *     filter = FilterPipes.tenantIsolation(filter);
     *     assertThat(filter.hasFilter("tenantId")).isTrue();
     * }
     * }</pre>
     *
     * @return array of method references for filter transformation pipeline
     */
    Method[] pipes() default {};

    /**
     * Reference to the handler method that processes filter requests and returns results.
     * <p>
     * The handler receives the filter AFTER all pipes have been applied.
     * </p>
     *
     * <h2>Convention over Configuration</h2>
     *
     * <h3>Default Behavior (no configuration needed)</h3>
     * If not specified, a default endpoint is generated with:
     * <ul>
     *   <li>Standard filtering logic applied to the repository</li>
     *   <li>No additional annotations</li>
     *   <li>Results returned according to the chosen strategy</li>
     * </ul>
     *
     * <pre>{@code
     * @Exposure(value = "users")  // Generates a standard endpoint
     * public class UserDTO { }
     * }</pre>
     *
     * <h3>Custom Handler with Annotations</h3>
     * Define a handler method to:
     * <ul>
     *   <li>Add security annotations (@PreAuthorize, @RolesAllowed, etc.)</li>
     *   <li>Add caching annotations (@Cacheable, @CacheEvict, etc.)</li>
     *   <li>Add rate limiting, logging, monitoring annotations</li>
     *   <li>Customize the filtering logic</li>
     *   <li>Pre/post-process the filter or results</li>
     * </ul>
     *
     * <pre>{@code
     * @Exposure(
     *   value = "users",
     *   handler = @Method("searchUsers")
     * )
     * public class UserDTO {
     *
     *   @PreAuthorize("hasRole('ADMIN')")
     *   @Cacheable(value = "userSearch", key = "#filter")
     *   @RateLimiter(name = "search-api")
     *   public static PaginatedData<UserDTO> searchUsers(
     *       FilterRequest filter,
     *       Pageable pageable,
     *       UserRepository repo
     *   ) {
     *     // Custom logic here
     *     return repo.findAll(filter.toSpecification(), pageable)
     *                .map(UserDTO::from);
     *   }
     * }
     * }</pre>
     *
     * <h3>External Handler Class</h3>
     * Share handler logic across multiple projections:
     *
     * <pre>{@code
     * public class SecurityHandlers {
     *
     *   @PreAuthorize("hasRole('ADMIN')")
     *   @Cacheable("adminSearch")
     *   @Transactional(readOnly = true)
     *   public static <T> PaginatedData<T> adminSearch(
     *       FilterRequest filter,
     *       Pageable pageable,
     *       JpaRepository<?, ?> repo,
     *       Function<?, T> mapper
     *   ) {
     *     // Shared admin search logic
     *     return repo.findAll(filter.toSpecification(), pageable)
     *                .map(mapper);
     *   }
     *
     *   @PreAuthorize("hasRole('USER')")
     *   @RateLimiter(name = "user-api")
     *   public static <T> List<T> userSearch(
     *       FilterRequest filter,
     *       JpaRepository<?, ?> repo,
     *       Function<?, T> mapper
     *   ) {
     *     // Shared user search logic
     *     return repo.findAll(filter.toSpecification())
     *                .stream()
     *                .map(mapper)
     *                .toList();
     *   }
     * }
     *
     * // Usage
     * @Exposure(
     *   value = "users",
     *   handler = @Method(
     *     type = SecurityHandlers.class,
     *     value = "adminSearch"
     *   )
     * )
     * public class UserDTO { }
     * }</pre>
     *
     * <h3>Handler Requirements</h3>
     * <ul>
     *   <li>Must be static</li>
     *   <li>Return type must match the chosen strategy</li>
     *   <li>First parameter: {@code FilterRequest filter}</li>
     *   <li>Strategy-specific parameters (e.g., {@code Pageable} for PAGINATED)</li>
     *   <li>Can declare additional parameters for dependency injection (repository, services, security context, etc.)</li>
     *   <li>All annotations on the handler method are copied to the generated endpoint</li>
     * </ul>
     *
     * <h3>Resolution Order</h3>
     * <ol>
     *   <li>If {@code type} is specified → use the method from that class</li>
     *   <li>If only {@code value} is specified → use the method from the annotated class</li>
     *   <li>If nothing is specified → generate default implementation</li>
     * </ol>
     *
     * @return method reference for custom endpoint implementation
     */
    Method handler() default @Method();

    /**
     * Name of the generated endpoint method.
     * <p>
     * Defaults to "search" + entity class name (e.g., "searchUsers" for User entity).
     * </p>
     *
     * <pre>{@code
     * @Exposure(value = "users", endpointName = "findAllUsers")
     * // Generates: POST /users/search mapped to findAllUsers(request)
     * }</pre>
     */
    String endpointName() default "";

    /**
     * Defines the available strategies for REST endpoint exposure.
     * <p>
     * Each strategy determines the return type and signature of the generated endpoint.
     * </p>
     */
    public enum Strategy {

        /**
         * Returns {@code PaginatedData<Map<String,Object>>} with pagination metadata.
         * <p>
         * Handler signature: {@code PaginatedData<T> method(FilterRequest filter)}
         * </p>
         */
        PROJECTED,

        /**
         * Returns {@code PaginatedData<T>} with pagination metadata. {@code T} is the class
         * on which the current annotation apply.
         * <p>
         * Handler signature: {@code PaginatedData<T> method(FilterRequest filter)}
         * </p>
         */
        PAGINATED,

        /**
         * Returns {@code List<T>} with all matching results. {@code T} is the class
         * on which the current annotation apply.
         * <p>
         * Handler signature: {@code List<T> method(FilterRequest filter)}
         * </p>
         */
        LIST,

        /**
         * Custom strategy - handler must define return type.
         * <p>
         * Handler signature: User-defined
         * </p>
         */
        CUSTOM
    }
}