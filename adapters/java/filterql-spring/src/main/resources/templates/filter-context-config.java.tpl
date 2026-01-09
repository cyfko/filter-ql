package io.github.cyfko.filterql.config;

import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.jpa.mappings.PredicateResolverMapping;
import io.github.cyfko.filterql.jpa.projection.InstanceResolver;
import io.github.cyfko.filterql.jpa.utils.ProjectionUtils;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.function.Supplier;
import javax.annotation.processing.Generated;

@Generated("io.github.cyfko.filterql.spring.processor.ExposureAnnotationProcessor")
@Configuration
public class FilterQlContextConfig {

${contextInstances}
}
