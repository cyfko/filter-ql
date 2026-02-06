package io.github.cyfko.filterql.spring.processor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.util.*;

import io.github.cyfko.filterql.spring.ExposedAs;
import io.github.cyfko.projection.Computed;
import io.github.cyfko.filterql.spring.processor.model.FieldMetadata;
import io.github.cyfko.filterql.spring.processor.model.SupportedType;
import io.github.cyfko.filterql.spring.support.DefaultOperatorStrategy;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.projection.Projection;
import io.github.cyfko.projection.metamodel.util.AnnotationProcessorUtils;

/**
 * Analyzes entity classes annotated for FilterQL and extracts metadata for all filterable fields.
 * <p>
 * Used by the FilterQL annotation processor to discover regular and virtual fields, determine their types,
 * supported operators, and generate metadata for code generation and runtime validation.
 * </p>
 *
 * <h2>Usage Context</h2>
 * <ul>
 *   <li>Invoked during annotation processing to build {@link FieldMetadata} for each entity</li>
 *   <li>Handles both persisted JPA fields and virtual fields defined via {@link ExposedAs}</li>
 *   <li>Supports custom generation strategies and exclusion rules</li>
 * </ul>
 *
 * <h2>Implementation Notes</h2>
 * <ul>
 *   <li>Uses {@link DefaultOperatorStrategy} for operator assignment</li>
 *   <li>Handles annotation mirror extraction for safe type resolution</li>
 *   <li>Extensible for custom field analysis logic</li>
 * </ul>
 *
 * @author cyfko
 * @since 1.0
 */
public class FieldAnalyzer {

    private final ProcessingEnvironment processingEnv;
    private final DefaultOperatorStrategy operatorStrategy;

    public FieldAnalyzer(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.operatorStrategy = new DefaultOperatorStrategy();
    }

    public List<FieldMetadata> analyzeProjection(TypeElement projectionClass) {
        List<FieldMetadata> fields = new ArrayList<>();

        for (Element element : projectionClass.getEnclosedElements()) {
            if (element.getKind() == ElementKind.FIELD && element.getAnnotation(Computed.class) == null) {
                VariableElement field = (VariableElement) element;
                String refName = extractRefName(field);
                SupportedType javaType = extractJavaType(field);
                Op[] operators = extractOperators(field, javaType);

                fields.add(FieldMetadata.regularField(
                        refName,
                        field.toString(),
                        operators
                ));
            } else if (element.getKind() == ElementKind.METHOD) {
                FieldMetadata fieldMetadata = scanVirtualFieldsInClass(projectionClass, null, element);
                if (fieldMetadata != null) {
                    fields.add(fieldMetadata);
                }
            }
        }

        List<ProviderMirror> virtualResolverClasses = getVirtualResolversSafe(projectionClass);
        for (ProviderMirror pm : virtualResolverClasses) {
            for (Element element : pm.typeElement.getEnclosedElements()) {
                if (element.getKind() != ElementKind.METHOD) continue;
                FieldMetadata fieldMetadata = scanVirtualFieldsInClass(pm.typeElement, pm.beanName, element);
                if (fieldMetadata != null) {
                    fields.add(fieldMetadata);
                }
            }
        }

        return fields;
    }

    private FieldMetadata scanVirtualFieldsInClass(TypeElement resolverClass, String beanName, Element element) {
        ExposedAs annotation = element.getAnnotation(ExposedAs.class);
        if (annotation == null) return null;

        ExecutableElement method = (ExecutableElement) element;
        TypeMirror returnType = method.getReturnType();
        List<? extends VariableElement> params = method.getParameters();
        
        // Only accept: PredicateResolver<E> method(String op, Object[] args)
        if (!returnType.toString().contains("PredicateResolver")) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "method annotated with @ExposedAs must return PredicateResolver<E>",
                    element
            );
            return null;
        }
        
        // Validate parameters: must be (String, Object[])
        if (params.size() != 2) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "method annotated with @ExposedAs must have exactly 2 parameters: (String op, Object[] args)",
                    element
            );
            return null;
        }
        String param0Type = params.get(0).asType().toString();
        String param1Type = params.get(1).asType().toString();
        if (!param0Type.equals("java.lang.String") || !param1Type.equals("java.lang.Object[]")) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "method annotated with @ExposedAs must have parameters (String op, Object[] args), found: (" + param0Type + ", " + param1Type + ")",
                    element
            );
            return null;
        }

        String methodName = method.getSimpleName().toString();
        boolean isStatic = method.getModifiers().contains(Modifier.STATIC);
        String resolverClassName = resolverClass.getQualifiedName().toString();

        return FieldMetadata.virtualField(
                annotation.value(),
                methodName,
                annotation.operators(),
                resolverClassName,
                beanName,
                isStatic
        );
    }

    private SupportedType extractJavaType(VariableElement field) {
        TypeMirror typeMirror = field.asType();
        return extractSupportedType(typeMirror);
    }

    private SupportedType extractSupportedType(TypeMirror typeMirror) {
        // D'abord essayer la détection par nom
        SupportedType fromName = SupportedType.fromTypeMirror(typeMirror);
        if (fromName != SupportedType.UNKNOWN) {
            return fromName;
        }

        // Ensuite vérifier si c'est une enum
        TypeElement typeElement = (TypeElement) processingEnv.getTypeUtils().asElement(typeMirror);
        if (typeElement != null && typeElement.getKind() == ElementKind.ENUM) {
            return SupportedType.ENUM;
        }

        return SupportedType.UNKNOWN;
    }

    private Op[] extractOperators(VariableElement field, SupportedType supportedType) {
        ExposedAs annotation = field.getAnnotation(ExposedAs.class);
        if (annotation != null && annotation.operators().length > 0) {
            return annotation.operators();
        }
        return operatorStrategy.getDefaultOperators(supportedType);
    }

    // Les méthodes utilitaires restent les mêmes
    private String extractRefName(VariableElement field) {
        ExposedAs annotation = field.getAnnotation(ExposedAs.class);
        if (annotation != null && !annotation.value().isEmpty()) {
            return annotation.value();
        }
        return toUpperSnakeCase(field.getSimpleName().toString());
    }

    private String toUpperSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
    }

    private record ProviderMirror(TypeElement typeElement, String beanName) {}

    /**
     * Récupère les virtualResolvers de manière safe (sans MirroredTypesException)
     */
    private List<ProviderMirror> getVirtualResolversSafe(TypeElement projectionClass) {
        Elements elementUtils = processingEnv.getElementUtils();
        List<ProviderMirror> resolvers = new ArrayList<>();

        AnnotationProcessorUtils.processExplicitFields(
                projectionClass,
                Projection.class.getName(),
                params -> {
                    List<Map<String,Object>> providers = (List<Map<String, Object>>) params.get("providers");
                    if (providers == null) return;

                    for (Map<String, Object> provider : providers) {
                        resolvers.add(new ProviderMirror(
                                elementUtils.getTypeElement((String) provider.get("value")),
                                (String) provider.get("name")
                        ));
                    }
                },
                null
        );

        return resolvers;
    }

}
