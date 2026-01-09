package ${packageName};

import io.github.cyfko.filterql.core.validation.PropertyReference;
import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.projection.metamodel.ProjectionRegistry;

import java.util.Set;

import javax.annotation.processing.Generated;

@Generated("io.github.cyfko.filterql.spring.processor.ExposureAnnotationProcessor")
public enum ${enumName} implements PropertyReference {

    ${constants};

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Override
    public Class<?> getType() {
${enumToFieldTypeSwitch}
    }

    @Override
    public Set<Op> getSupportedOperators() {
${enumToOperatorsSwitch}
    }

    @Override
    public Class<?> getEntityType() {
        return ${entityClass};
    }
}
