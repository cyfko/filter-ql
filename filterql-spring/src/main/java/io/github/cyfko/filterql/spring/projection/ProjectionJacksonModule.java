package io.github.cyfko.filterql.spring.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * Jackson module registering the {@link ProjectionProxySerializer} for
 * automatic serialization of projection proxy instances.
 *
 * <p>
 * When registered as a Spring Bean (via {@code FilterQlAutoConfiguration}),
 * Spring Boot automatically adds it to the global {@code ObjectMapper}.
 * </p>
 *
 * @author Frank KOSSI
 * @since 4.0.0
 */
public class ProjectionJacksonModule extends SimpleModule {

    public ProjectionJacksonModule() {
        super("FilterQL-Projection");
        addSerializer(ProjectionProxy.class, new ProjectionProxySerializer());
    }
}
