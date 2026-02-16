package io.github.cyfko.filterql.spring.projection;

import java.util.Map;

/**
 * Internal marker interface added to projection proxies for fast detection
 * by the Jackson serializer.
 *
 * <p>
 * <b>NOT intended to be implemented by users.</b> This interface is
 * automatically
 * added to proxies created by {@link ProjectionProxyFactory}.
 * </p>
 *
 * @author Frank KOSSI
 * @since 4.0.0
 */
public interface ProjectionProxy {

    /**
     * Returns the underlying projected data map.
     * Keys are DTO field names; values are the projected values (may be
     * {@code null}).
     *
     * @return the projected data map (never {@code null})
     */
    Map<String, Object> _getProjectedData();

    /**
     * Returns the projection interface type that this proxy implements.
     *
     * @return the projection interface class
     */
    Class<?> _getProjectionInterface();
}
