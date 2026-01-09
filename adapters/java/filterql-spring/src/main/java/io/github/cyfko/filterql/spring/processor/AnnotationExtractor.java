package io.github.cyfko.filterql.spring.processor;

import io.github.cyfko.projection.MethodReference;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.*;
import java.util.stream.Collectors;

public class AnnotationExtractor {
    private final String defaultMethodName;

    private Types typeUtils;
    private Messager messager;

    // Track imports needed for generated classes
    private final Set<String> requiredImports = new LinkedHashSet<>();

    public AnnotationExtractor(String defaultMethodName) {
        Objects.requireNonNull(defaultMethodName, "defaultMethodName cannot be null");
        if (defaultMethodName.isBlank()) {
            throw new IllegalArgumentException("defaultMethodName cannot be blank");
        }
        this.defaultMethodName = defaultMethodName;
    }

    /**
     * Extract annotations from the template method based on MethodReference configuration.
     */
    public ExtractedAnnotation extractAnnotations(
            ProcessingEnvironment processingEnv,
            TypeElement exposedElement,
            MethodReference methodRef
    ) {
        this.messager = processingEnv.getMessager();
        this.typeUtils = processingEnv.getTypeUtils();
        Elements elementUtils = processingEnv.getElementUtils();

        // Determine target class
        TypeElement targetClass;
        if (isClassSpecified(methodRef)) {
            // External class specified
            String className = getClassName(methodRef);
            if (className == null) {
                targetClass = exposedElement;
            } else {
                targetClass = elementUtils.getTypeElement(className);
            }

            if (targetClass == null) {
                messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "Cannot find class: " + className,
                        exposedElement
                );
                return new ExtractedAnnotation() {
                    @Override
                    public List<AnnotationInfo> getAnnotations() {
                        return List.of();
                    }

                    @Override
                    public Set<String> getRequiredImports() {
                        return Set.of();
                    }
                };
            }
        } else {
            // Use the entity class itself
            targetClass = exposedElement;
        }

        // Determine method name
        String methodName = methodRef.method().isEmpty()
                ? defaultMethodName
                : methodRef.method();

        // Extract annotations from the method
        List<AnnotationInfo> annotations = extractAnnotationsFromMethod(
                targetClass,
                methodName,
                exposedElement
        );

        // If no method found and using convention, provide helpful message
        if (annotations.isEmpty() && methodRef.method().isEmpty() && !isClassSpecified(methodRef)) {
            messager.printMessage(
                    Diagnostic.Kind.NOTE,
                    "No '" + defaultMethodName + "()' method found. " +
                            "Generating endpoint without custom annotations. " +
                            "To customize, add: private static void " + defaultMethodName + "() {}",
                    exposedElement
            );
        }

        return new  ExtractedAnnotation() {
            @Override
            public List<AnnotationInfo> getAnnotations() {
                return annotations;
            }

            @Override
            public Set<String> getRequiredImports() {
                return requiredImports;
            }
        };
    }

    /**
     * Extract annotations from a specific method in a class.
     */
    private List<AnnotationInfo> extractAnnotationsFromMethod(
            TypeElement classElement,
            String methodName,
            TypeElement contextElement
    ) {
        List<AnnotationInfo> annotations = new ArrayList<>();

        // Find the method
        ExecutableElement targetMethod = null;
        for (Element enclosedElement : classElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.METHOD &&
                    enclosedElement.getSimpleName().toString().equals(methodName)) {
                targetMethod = (ExecutableElement) enclosedElement;
                break;
            }
        }

        if (targetMethod == null) {
            // Default method not found ! It is not an error !
            return annotations;
        }

        // Validate method signature
        if (!validateMethodSignature(targetMethod, methodName)) {
            return annotations;
        }

        // Extract all annotations from the method
        for (AnnotationMirror mirror : targetMethod.getAnnotationMirrors()) {
            AnnotationInfo annotationInfo = buildAnnotationInfo(mirror);
            annotations.add(annotationInfo);

            // Record import
            requiredImports.add(annotationInfo.qualifiedName);
        }

        return annotations;
    }

    /**
     * Validate that the method has the correct signature:
     * - void return type
     * - no parameters
     */
    private boolean validateMethodSignature(ExecutableElement method, String methodName) {
        Set<Modifier> modifiers = method.getModifiers();
        boolean isValid = true;

        // Must return void
        if (method.getReturnType().getKind() != TypeKind.VOID) {
            messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Method '" + methodName + "' must return void. " +
                            "Expected signature: private static void " + methodName + "()",
                    method
            );
            isValid = false;
        }

        // Must take no parameters
        if (!method.getParameters().isEmpty()) {
            messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Method '" + methodName + "' must take no parameters. " +
                            "Expected signature: private static void " + methodName + "()",
                    method
            );
            isValid = false;
        }

        return isValid;
    }

    /**
     * Build annotation information from an AnnotationMirror.
     */
    private AnnotationInfo buildAnnotationInfo(AnnotationMirror mirror) {
        DeclaredType annotationType = mirror.getAnnotationType();
        TypeElement annotationElement = (TypeElement) annotationType.asElement();

        String qualifiedName = annotationElement.getQualifiedName().toString();
        String simpleName = annotationElement.getSimpleName().toString();

        // Build the annotation string with its attributes
        String annotationString = buildAnnotationString(mirror, simpleName);

        return new AnnotationInfo(qualifiedName, simpleName, annotationString);
    }

    /**
     * Build the complete annotation string including attributes.
     */
    private String buildAnnotationString(AnnotationMirror mirror, String simpleName) {
        StringBuilder sb = new StringBuilder("@").append(simpleName);

        Map<? extends ExecutableElement, ? extends AnnotationValue> values =
                mirror.getElementValues();

        if (!values.isEmpty()) {
            sb.append("(");

            boolean isFirst = true;
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                    values.entrySet()) {

                if (!isFirst) {
                    sb.append(", ");
                }
                isFirst = false;

                String attrName = entry.getKey().getSimpleName().toString();

                // Omit "value =" if it's the only attribute
                if (values.size() == 1 && "value".equals(attrName)) {
                    sb.append(formatAnnotationValue(entry.getValue()));
                } else {
                    sb.append(attrName).append(" = ");
                    sb.append(formatAnnotationValue(entry.getValue()));
                }
            }

            sb.append(")");
        }

        return sb.toString();
    }

    /**
     * Format an annotation value (handles strings, arrays, nested annotations, enums, etc.)
     */
    private String formatAnnotationValue(AnnotationValue value) {
        Object val = value.getValue();

        if (val instanceof String) {
            return "\"" + escapeString((String) val) + "\"";
        } else if (val instanceof Character) {
            return "'" + val + "'";
        } else if (val instanceof Class<?> clazz) {
            requiredImports.add(clazz.getCanonicalName());
            return clazz.getSimpleName() + ".class";
        } else if (val instanceof TypeMirror typeMirror) {
            TypeElement typeElement = (TypeElement) typeUtils.asElement(typeMirror);
            if (typeElement != null) {
                requiredImports.add(typeElement.getQualifiedName().toString());
                return typeElement.getSimpleName() + ".class";
            }
            return val.toString();
        } else if (val instanceof List) {
            @SuppressWarnings("unchecked")
            List<? extends AnnotationValue> list = (List<? extends AnnotationValue>) val;

            if (list.isEmpty()) {
                return "{}";
            }

            if (list.size() == 1) {
                return formatAnnotationValue(list.get(0));
            }

            return "{" + list.stream()
                    .map(this::formatAnnotationValue)
                    .collect(Collectors.joining(", ")) + "}";
        } else if (val instanceof AnnotationMirror nestedMirror) {
            // Nested annotation
            TypeElement nestedElement = (TypeElement) nestedMirror.getAnnotationType().asElement();
            requiredImports.add(nestedElement.getQualifiedName().toString());
            return buildAnnotationString(nestedMirror, nestedElement.getSimpleName().toString());
        } else if (val instanceof VariableElement varElement) {
            // Enum constant
            TypeElement enumElement = (TypeElement) varElement.getEnclosingElement();
            requiredImports.add(enumElement.getQualifiedName().toString());
            return enumElement.getSimpleName() + "." + varElement.getSimpleName();
        }

        return val.toString();
    }

    /**
     * Check if a class is specified in MethodReference.
     */
    private boolean isClassSpecified(MethodReference methodRef) {
        try {
            Class<?> type = methodRef.type();
            return type != void.class;
        } catch (Exception e) {
            // In annotation processing, accessing Class<?> can throw MirroredTypeException
            // We need to use a different approach
            return true; // If exception thrown, a type was specified
        }
    }

    /**
     * Get the class name from MethodReference using mirrors.
     */
    private String getClassName(MethodReference methodRef) {
        try {
            // This will throw MirroredTypeException
            methodRef.type();
            return null;
        } catch (javax.lang.model.type.MirroredTypeException e) {
            TypeMirror typeMirror = e.getTypeMirror();
            TypeElement typeElement = (TypeElement) typeUtils.asElement(typeMirror);
            return typeElement == null ? null : typeElement.getQualifiedName().toString();
        }
    }

    /**
     * Escape special characters in strings.
     */
    private String escapeString(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ==================== Classes ====================

    /**
     * Holds information about an extracted annotation.
     */
    public record AnnotationInfo(String qualifiedName, String simpleName, String annotationString) {
    }

    public interface ExtractedAnnotation{
        List<AnnotationInfo> getAnnotations();
        Set<String> getRequiredImports();
    }
}
