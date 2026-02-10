package io.github.cyfko.filterql.spring.generator;

import io.github.cyfko.filterql.spring.Exposure;
import io.github.cyfko.filterql.spring.processor.AnnotationExtractor;
import io.github.cyfko.filterql.spring.util.StringUtils;
import io.github.cyfko.projection.MethodReference;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import java.io.IOException;
import java.util.*;

/**
 * Generates the source code for the FilterQL REST search controller based on entity and DTO metadata.
 * <p>
 * Used by the FilterQL annotation processor to produce controller classes exposing search endpoints
 * for each filtered entity, including DTO mapping, request validation, and repository integration.
 * </p>
 *
 * <h2>Usage Context</h2>
 * <ul>
 *   <li>Invoked during annotation processing for each {@link Exposure} entity</li>
 *   <li>Produces Java source code for REST controllers via templating</li>
 *   <li>Handles endpoint generation, import management, and DTO mapping logic</li>
 * </ul>
 *
 * <h2>Extension Points</h2>
 * <ul>
 *   <li>Custom templates via {@link TemplateEngine}</li>
 *   <li>Override for advanced endpoint or mapping logic</li>
 * </ul>
 *
 * @author cyfko
 * @since 1.0
 */
public class FilterControllerGenerator {
    private static final String DEFAULT_ANNOTATION_LOOKUP_METHOD_NAME = "searchEndpoint";

    private final TemplateEngine templateEngine;

    // On utilise Set pour éviter les doublons
    private final StringBuilder searchEndpoints = new StringBuilder();
    private final Set<String> annotationsImports = new LinkedHashSet<>();

    /**
     * Constructs a generator using the provided template engine.
     *
     * @param templateEngine engine for loading and processing templates
     */
    public FilterControllerGenerator(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * Registers a new search endpoint for the given entity and DTO mapping.
     *
     * @param processingEnv annotation processing environment
     * @param projectionClass projection class element
     * @param packageName target package for the controller
     * @param projectionSimpleName simple name of the entity
     * @param propertyRefEnumName name of the property reference enum
     */
    public void register(
        ProcessingEnvironment processingEnv,
        TypeElement projectionClass,
        String packageName,
        String projectionSimpleName,
        String propertyRefEnumName
    ) {
        Exposure exposure = projectionClass.getAnnotation(Exposure.class);
        if (exposure == null) return;

        // --- Nom exposé sécurisé
        String exposedName = toExposedName(exposure,projectionSimpleName);
        String basePath = toBasePath(exposure);
        String listItemType = "Map<String,Object>";

        try {
            String searchTemplate = templateEngine.loadTemplate("search-endpoint.java.tpl");
            String decorators = extractAnnotationDecorators(processingEnv, projectionClass, exposure.annotationsFrom());

            Map<String, Object> searchContext = new HashMap<>();
            searchContext.put("basePath", basePath);
            searchContext.put("exposedName", exposedName);
            searchContext.put("listItemType", listItemType);
            searchContext.put("methodName", "search" + projectionSimpleName);
            searchContext.put("fqEnumName", packageName + "." + propertyRefEnumName);
            searchContext.put("annotationDecorators", decorators);

            // generated code snippet
            String searchSnippet = templateEngine.process(searchTemplate, searchContext);
            if (!searchEndpoints.toString().contains(searchSnippet)) {
                searchEndpoints.append(searchSnippet);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load search-endpoint template", e);
        }

    }

    private String extractAnnotationDecorators(ProcessingEnvironment processingEnv, TypeElement projectionClass, MethodReference mref) {
        AnnotationExtractor annotationExtractor = new AnnotationExtractor(DEFAULT_ANNOTATION_LOOKUP_METHOD_NAME);
        final var extractedAnnotation = annotationExtractor.extractAnnotations(processingEnv, projectionClass, mref);
        final StringBuilder decorators = new StringBuilder();

        for (var ea : extractedAnnotation.getAnnotations()){
            decorators.append("\n    ").append(ea.annotationString());
        }

        extractedAnnotation.getRequiredImports().forEach(importName -> {
            if (annotationsImports.contains(importName)) return;
            annotationsImports.add("import " + importName + ";");
        });

        return decorators.toString();
    }

    /**
     * Generates the source code for the search controller.
     *
     * @return Java source code for the controller
     * @throws IOException if template loading or processing fails
     */
    public String generate() throws IOException {
        String template = templateEngine.loadTemplate("search-controller.java.tpl");
        Map<String, Object> context = new HashMap<>();
        context.put("annotationsImports", String.join("\n", annotationsImports));
        context.put("searchEndpoints", searchEndpoints.toString());
        return templateEngine.process(template, context);
    }

    /**
     * Computes the exposed REST resource name for the given entity, based on the specified {@link Exposure} annotation.
     * <p>
     * This method retrieves the {@code value} configured in the embedded {@link Exposure} annotation.
     * If the value is not set or blank, it falls back to converting the entity simple name from camelCase to kebab-case
     * following standard naming conventions.
     * </p>
     *
     * @param exposure the {@link Exposure} exposure configuration
     * @param entitySimpleName the simple class name of the entity (typically without package)
     * @return the exposed REST resource name in kebab-case
     */
    public static String toExposedName(Exposure exposure, String entitySimpleName) {
        return (exposure.value() != null && !exposure.value().isBlank()) ?
                exposure.value().trim() :
                StringUtils.camelToKebabCase(StringUtils.toCamelCase(entitySimpleName));
    }

    /**
     * Computes the base URI path prefix for REST endpoints of the given filtered entity, based on the annotation configuration.
     * <p>
     * Returns the {@code basePath} specified in the embedded {@link Exposure} annotation,
     * trimming whitespace or returning an empty string if unspecified.
     * </p>
     *
     * @param exposure the {@link Exposure} endpoint exposure configuration
     * @return the base URI path prefix for REST endpoints, or empty string if none is configured
     */
    public static String toBasePath(Exposure exposure) {
        return exposure.basePath() != null ? exposure.basePath().trim() : "";
    }
}
