package io.github.cyfko.filterql.spring.config;

import io.github.cyfko.filterql.jpa.spi.PredicateResolver;
import io.github.cyfko.filterql.jpa.spi.PredicateResolverMapping;
import io.github.cyfko.filterql.jpa.spi.InstanceResolver;
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
