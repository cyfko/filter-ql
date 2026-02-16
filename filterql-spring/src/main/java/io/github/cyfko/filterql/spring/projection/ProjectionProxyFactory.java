package io.github.cyfko.filterql.spring.projection;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Factory for creating dynamic JDK proxy implementations of projection
 * interfaces,
 * backed by a {@code Map<String, Object>} from {@code RowBuffer::toMap()}.
 *
 * <h2>Behavior</h2>
 * <ul>
 * <li>Getter methods ({@code getXxx()}, {@code isXxx()}, {@code hasXxx()})
 * resolve to
 * map keys using JavaBean naming convention</li>
 * <li>If the key exists in the map, the value is returned (even if
 * {@code null})</li>
 * <li>If the key does NOT exist, {@link FieldNotProjectedException} is
 * thrown</li>
 * </ul>
 *
 * <h2>Jackson Integration</h2>
 * <p>
 * The returned proxy also implements {@link ProjectionProxy}, enabling the
 * {@link ProjectionProxySerializer} to serialize only projected fields.
 * </p>
 *
 * @author Frank KOSSI
 * @since 4.0.0
 */
public final class ProjectionProxyFactory {

    private ProjectionProxyFactory() {
    }

    /**
     * Creates a dynamic proxy implementing the given projection interface.
     *
     * @param projectionInterface the projection interface (annotated with
     *                            {@code @Projection})
     * @param data                the projected data from {@code RowBuffer::toMap()}
     * @param <T>                 the projection interface type
     * @return a proxy instance implementing {@code T} and {@link ProjectionProxy}
     * @throws IllegalArgumentException if projectionInterface is not an interface
     *                                  or data is null
     */
    @SuppressWarnings("unchecked")
    public static <T> T create(Class<T> projectionInterface, Map<String, Object> data) {
        Objects.requireNonNull(projectionInterface, "projectionInterface must not be null");
        Objects.requireNonNull(data, "data must not be null");
        if (!projectionInterface.isInterface()) {
            throw new IllegalArgumentException(
                    projectionInterface.getName() + " is not an interface. "
                            + "Only interfaces can be used as projection types.");
        }

        Map<String, Object> immutableData = Collections.unmodifiableMap(data);
        InvocationHandler handler = new ProjectionInvocationHandler(projectionInterface, immutableData);

        return (T) Proxy.newProxyInstance(
                projectionInterface.getClassLoader(),
                new Class<?>[] { projectionInterface, ProjectionProxy.class },
                handler);
    }

    /**
     * Checks if the given object is a projection proxy created by this factory.
     *
     * @param obj the object to check
     * @return {@code true} if the object is a projection proxy
     */
    public static boolean isProjectionProxy(Object obj) {
        return obj instanceof ProjectionProxy;
    }

    // ==================== InvocationHandler ====================

    private static final class ProjectionInvocationHandler implements InvocationHandler {

        private final Class<?> projectionInterface;
        private final Map<String, Object> data;

        ProjectionInvocationHandler(Class<?> projectionInterface, Map<String, Object> data) {
            this.projectionInterface = projectionInterface;
            this.data = data;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();

            // --- ProjectionProxy marker interface methods ---
            if (method.getDeclaringClass() == ProjectionProxy.class) {
                return switch (methodName) {
                    case "_getProjectedData" -> data;
                    case "_getProjectionInterface" -> projectionInterface;
                    default -> throw new UnsupportedOperationException(methodName);
                };
            }

            // --- Object methods ---
            if (method.getDeclaringClass() == Object.class) {
                return switch (methodName) {
                    case "toString" -> toStringImpl();
                    case "equals" -> equalsImpl(args[0]);
                    case "hashCode" -> hashCodeImpl();
                    default -> throw new UnsupportedOperationException(methodName);
                };
            }

            // --- Getter resolution ---
            String fieldName = extractFieldName(method);
            if (fieldName != null) {
                if (data.containsKey(fieldName)) {
                    return data.get(fieldName);
                }
                throw new FieldNotProjectedException(fieldName, projectionInterface.getSimpleName());
            }

            throw new UnsupportedOperationException(
                    "Method '" + methodName + "' on " + projectionInterface.getSimpleName()
                            + " is not a recognized getter (getXxx/isXxx/hasXxx).");
        }

        private String toStringImpl() {
            return projectionInterface.getSimpleName() + "{projected=" + data.keySet() + ", values=" + data + "}";
        }

        private boolean equalsImpl(Object other) {
            if (other == null)
                return false;
            if (!(other instanceof ProjectionProxy otherProxy))
                return false;
            return projectionInterface.equals(otherProxy._getProjectionInterface())
                    && data.equals(otherProxy._getProjectedData());
        }

        private int hashCodeImpl() {
            return Objects.hash(projectionInterface, data);
        }
    }

    // ==================== Field Name Extraction ====================

    /**
     * Extracts the JavaBean field name from a getter method name.
     *
     * <ul>
     * <li>{@code getFirstName} → {@code "firstName"}</li>
     * <li>{@code isActive} → {@code "active"}</li>
     * <li>{@code hasPermission} → {@code "permission"}</li>
     * </ul>
     *
     * @param method the method to extract the field name from
     * @return the field name, or {@code null} if not a recognized getter
     */
    static String extractFieldName(Method method) {
        String name = method.getName();
        int paramCount = method.getParameterCount();

        // Getters must have no parameters
        if (paramCount != 0) {
            return null;
        }

        if (name.startsWith("get") && name.length() > 3) {
            return Character.toLowerCase(name.charAt(3)) + name.substring(4);
        }
        if (name.startsWith("is") && name.length() > 2) {
            return Character.toLowerCase(name.charAt(2)) + name.substring(3);
        }
        if (name.startsWith("has") && name.length() > 3) {
            return Character.toLowerCase(name.charAt(3)) + name.substring(4);
        }

        return null;
    }
}
