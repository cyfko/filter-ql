package ${packageName};

import io.github.cyfko.filterql.core.api.PropertyReference;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.jpametamodel.ProjectionRegistry;

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
