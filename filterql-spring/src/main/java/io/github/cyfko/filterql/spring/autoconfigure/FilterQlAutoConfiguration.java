package io.github.cyfko.filterql.spring.autoconfigure;

import io.github.cyfko.filterql.jpa.JpaFilterContext;
import io.github.cyfko.filterql.spring.support.FilterContextRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@AutoConfiguration
@ComponentScan(basePackages = "io.github.cyfko.filterql.spring")
@ConditionalOnClass(JpaFilterContext.class)
@EnableConfigurationProperties(FilterQlProperties.class)
public class FilterQlAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FilterContextRegistry filterContextRegistry(List<JpaFilterContext<?>> contexts) {
        return new FilterContextRegistry(contexts);
    }

}
