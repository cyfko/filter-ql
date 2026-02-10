package io.github.cyfko.filterql.spring.processor;

import com.google.auto.service.AutoService;
import io.github.cyfko.filterql.spring.generator.*;
import io.github.cyfko.filterql.spring.processor.model.FieldMetadata;
import io.github.cyfko.projection.Projection;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Set;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;

/**
 * Annotation processor for the {@link Projection} annotation,
 * responsible for generating FilterQL integration artifacts at compile time.
 * <p>
 * This processor analyzes entity classes annotated with {@link Projection},
 * extracts filterable field metadata,
 * and generates the following:
 * <ul>
 * <li>Type-safe property reference enums</li>
 * <li>Spring configuration classes for FilterQL integration</li>
 * <li>REST controllers for filter endpoints if also annotated with
 * {@link io.github.cyfko.filterql.spring.Exposure}</li>
 * <li>Metadata registry implementations</li>
 * </ul>
 * </p>
 *
 * <h2>Usage Context</h2>
 * <ul>
 * <li>Invoked automatically during Java compilation (Maven/Gradle)</li>
 * <li>Enables zero-boilerplate integration of FilterQL in Spring Boot
 * applications</li>
 * <li>Ensures type safety and validation for all filterable entities</li>
 * </ul>
 *
 * <h2>Implementation Notes</h2>
 * <ul>
 * <li>Uses {@link com.google.auto.service.AutoService} for processor
 * registration</li>
 * <li>Supports Java 17 source version</li>
 * <li>Handles errors and warnings via the annotation processing API</li>
 * <li>Extensible via injected generators and analyzers</li>
 * </ul>
 *
 * <h2>Generated Artifacts</h2>
 * <ul>
 * <li>PropertyRef enums for each entity</li>
 * <li>Spring configuration classes</li>
 * <li>REST controllers for filter endpoints</li>
 * <li>Field metadata registry implementations</li>
 * </ul>
 *
 * @author cyfko
 * @since 1.0
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("io.github.cyfko.projection.Projection")
public class ExposureAnnotationProcessor extends AbstractProcessor {
    private static String GENERATED_BASE_PACKAGE = "io.github.cyfko.filterql.spring";

    private FieldAnalyzer fieldAnalyzer;
    private TemplateEngine templateEngine;
    private PropertyRefEnumGenerator enumGenerator;
    private FilterContextGenerator configGenerator;
    private FilterControllerGenerator controllerGenerator;
    private AnnotationExtractor annotationExtractor;

    // BLOC DE DEBUG - s'exécute au chargement de la classe
    static {
        System.out.println("🚨🚨🚨 ExposureAnnotationProcessor CLASS LOADED 🚨🚨🚨");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.fieldAnalyzer = new FieldAnalyzer(processingEnv);
        this.templateEngine = new TemplateEngine();
        this.enumGenerator = new PropertyRefEnumGenerator(templateEngine);
        this.configGenerator = new FilterContextGenerator(templateEngine);
        this.controllerGenerator = new FilterControllerGenerator(templateEngine);
        log("ExposureAnnotationProcessor initialized");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        log("=== PROCESS ROUND START ===");
        log("Annotations: " + annotations);

        if (annotations.isEmpty()) {
            log("No annotations to process");
            return false;
        }

        for (TypeElement annotation : annotations) {
            Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
            log("Found " + annotatedElements.size() + " elements with @" + annotation.getSimpleName());

            for (Element element : annotatedElements) {
                if (element.getKind() != ElementKind.CLASS)
                    continue;

                log("Processing element: " + element.getSimpleName() + " (" + element.getKind() + ")");

                try {
                    processProjection((TypeElement) element);
                } catch (Exception e) {
                    error("Failed to generate FilterQL code: " + e.getMessage(), element);
                    e.printStackTrace();
                }
            }
        }

        // Generate Spring-boot config
        try {
            String configCode = configGenerator.generate();
            writeSourceFile(GENERATED_BASE_PACKAGE + ".config", "FilterQlContextConfig", configCode);
            note("Generated controller file " + GENERATED_BASE_PACKAGE + ".config.FilterQlContextConfig");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Generate the search controller
        try {
            String controllerCode = controllerGenerator.generate();
            writeSourceFile(GENERATED_BASE_PACKAGE + ".controller", "FilterQlController", controllerCode);
            note("Generated controller file " + GENERATED_BASE_PACKAGE + ".controller.FilterQlController");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log("=== PROCESS ROUND END ===");
        return false; // Permettre la coexistence avec d'autres processeurs d'annotations traitant
                      // @Projection
    }

    private void processProjection(TypeElement projectionClass) throws IOException {
        // 1. Extraire les métadonnées
        String packageName = getPackageName(projectionClass);
        String projectionSimpleName = projectionClass.getSimpleName().toString();
        Projection annotation = projectionClass.getAnnotation(Projection.class);

        if (annotation == null) {
            log("WARNING: @Projection annotation not found on " + projectionSimpleName);
            return;
        }

        String entityClassName = extractEntityClassName(annotation);
        String enumName = projectionSimpleName + "_";

        log("Package: " + packageName + ", Enum: " + enumName);

        // 2. Analyser les champs
        List<FieldMetadata> fields = fieldAnalyzer.analyzeProjection(projectionClass);
        log("Found " + fields.size() + " filterable fields");

        if (fields.isEmpty()) {
            warning("No filterable fields found in " + projectionSimpleName, projectionClass);
            return;
        }

        // 3. Générer l'enum PropertyRef
        String enumCode = enumGenerator.generate(
                packageName,
                projectionSimpleName,
                enumName,
                fields);
        writeSourceFile(packageName, enumName, enumCode);
        log("Generated enum: " + packageName + "." + enumName);

        // 4. Générer la configuration Spring
        try {
            configGenerator.register(packageName, enumName, fields, entityClassName);
        } catch (Exception e) {
            error(e.getMessage(), projectionClass);
        }

        // 5. Générer le contrôleur de recherche Spring
        try {
            controllerGenerator.register(
                    processingEnv,
                    projectionClass,
                    packageName,
                    projectionSimpleName,
                    enumName);
        } catch (Exception e) {
            error(e.getMessage(), projectionClass);
        }
    }

    private String extractEntityClassName(Projection annotation) {
        try {
            return annotation.from().getName();
        } catch (MirroredTypeException e) {
            TypeMirror typeMirror = e.getTypeMirror();
            return typeMirror.toString();
        }
    }

    private void writeSourceFile(String packageName, String className, String code)
            throws IOException {
        String qualifiedName = packageName + "." + className;
        log("Writing source file: " + qualifiedName);

        JavaFileObject file = processingEnv.getFiler().createSourceFile(qualifiedName);
        try (Writer writer = file.openWriter()) {
            writer.write(code);
        }
        log("Successfully wrote: " + qualifiedName);
    }

    private String getPackageName(TypeElement element) {
        return processingEnv.getElementUtils()
                .getPackageOf(element)
                .getQualifiedName()
                .toString();
    }

    private void log(String message) {
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.NOTE,
                "[FilterQL] " + message);
    }

    private void error(String message, Element element) {
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "[FilterQL] " + message,
                element);
    }

    private void warning(String message, Element element) {
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.WARNING,
                "[FilterQL] " + message,
                element);
    }

    private void note(String message) {
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.NOTE,
                "[FilterQL] " + message);
    }
}
