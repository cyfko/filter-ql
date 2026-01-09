package io.github.cyfko.filterql.spring.service.impl;

import io.github.cyfko.filterql.jpa.exception.InstanceResolutionException;
import io.github.cyfko.filterql.jpa.projection.InstanceResolver;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring Framework implementation of {@link InstanceResolver}.
 * 
 * <p>This resolver integrates with Spring's ApplicationContext to resolve
 * beans for any purpose.</p>
 * @since 1.0.0
 */
@Component
public class SpringProviderResolver implements InstanceResolver, ApplicationContextAware {
    
    private static ApplicationContext applicationContext;
    
    @Override
    public void setApplicationContext(ApplicationContext context) {
        SpringProviderResolver.applicationContext = context;
    }

    @Override
    public <T> T resolve(Class<T> providerClass, String beanName) {
        if (applicationContext == null) {
            throw new InstanceResolutionException("Spring ApplicationContext not initialized. Ensure Spring is properly configured.");
        }

        try {
            if (beanName != null && !beanName.isEmpty()) {
                return applicationContext.getBean(beanName, providerClass);
            } else {
                return applicationContext.getBean(providerClass);
            }
        } catch (NoSuchBeanDefinitionException e) {
            // Bean not found - return null to signal static method usage
            return null;
        } catch (Exception e) {
            throw new InstanceResolutionException(
                    "Failed to resolve provider: " + providerClass.getName() +
                    ((beanName == null || beanName.isEmpty()) ? "" : " (name: " + beanName + ")"), e);
        }
    }
}