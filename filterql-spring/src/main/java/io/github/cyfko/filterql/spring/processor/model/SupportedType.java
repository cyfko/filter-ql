package io.github.cyfko.filterql.spring.processor.model;

import javax.lang.model.type.TypeMirror;
import java.util.Set;

public enum SupportedType {
    STRING(String.class),
    INTEGER(Integer.class, int.class),
    LONG(Long.class, long.class),
    DOUBLE(Double.class, double.class),
    FLOAT(Float.class, float.class),
    BOOLEAN(Boolean.class, boolean.class),
    LOCAL_DATE(java.time.LocalDate.class),
    LOCAL_DATE_TIME(java.time.LocalDateTime.class),
    LOCAL_TIME(java.time.LocalTime.class),
    UUID(java.util.UUID.class),
    BIG_INTEGER(java.math.BigInteger.class),
    BIG_DECIMAL(java.math.BigDecimal.class),
    ENUM,
    COLLECTION_MAP(java.util.Map.class),
    COLLECTION_LIST(java.util.List.class),
    COLLECTION_SET(java.util.Set.class),
    UNKNOWN;

    private final Set<Class<?>> supportedClasses;

    SupportedType(Class<?>... classes) {
        this.supportedClasses = Set.of(classes);
    }

    // Constructeur par défaut (pour ENUM et UNKNOWN)
    SupportedType() {
        this.supportedClasses = Set.of();
    }

    public static SupportedType fromClass(Class<?> clazz) {
        for (SupportedType type : values()) {
            if (type.supportedClasses.contains(clazz)) {
                return type;
            }
        }

        if (clazz != null && clazz.isEnum()) {
            return ENUM;
        }

        return UNKNOWN;
    }

    public static SupportedType fromTypeMirror(TypeMirror typeMirror) {
        String typeName = typeMirror.toString();
        return fromTypeName(typeName);
    }

    public static SupportedType fromTypeName(String typeName) {
        return switch (typeName) {
            case "java.lang.String", "String" -> STRING;
            case "java.lang.Integer", "Integer", "int" -> INTEGER;
            case "java.lang.Long", "Long", "long" -> LONG;
            case "java.lang.Double", "Double", "double" -> DOUBLE;
            case "java.lang.Float", "Float", "float" -> FLOAT;
            case "java.lang.Boolean", "Boolean", "boolean" -> BOOLEAN;
            case "java.time.LocalDate", "LocalDate" -> LOCAL_DATE;
            case "java.time.LocalDateTime", "LocalDateTime" -> LOCAL_DATE_TIME;
            case "java.time.LocalTime", "LocalTime" -> LOCAL_TIME;
            case "java.util.UUID", "UUID" -> UUID;
            case "java.math.BigInteger", "BigInteger" -> BIG_INTEGER;
            case "java.math.BigDecimal", "BigDecimal" -> BIG_DECIMAL;
            case "java.util.Map", "Map<String,Object>" -> COLLECTION_MAP;
            case "java.util.List", "List<Object>" -> COLLECTION_LIST;
            case "java.util.Set", "Set<Object>" -> COLLECTION_SET;
            default ->
                // Pour les autres types, on retourne UNKNOWN
                // La détection des enums se fait au niveau du TypeMirror
                    UNKNOWN;
        };
    }

    public String getClassName() {
        return switch (this) {
            case STRING -> "String";
            case INTEGER -> "Integer";
            case LONG -> "Long";
            case DOUBLE -> "Double";
            case FLOAT -> "Float";
            case BOOLEAN -> "Boolean";
            case LOCAL_DATE -> "LocalDate";
            case LOCAL_DATE_TIME -> "LocalDateTime";
            case LOCAL_TIME -> "LocalTime";
            case UUID -> "UUID";
            case BIG_INTEGER -> "BigInteger";
            case BIG_DECIMAL -> "BigDecimal";
            case COLLECTION_MAP -> "Map<String,Object>";
            case COLLECTION_LIST -> "List<Object>";
            case COLLECTION_SET -> "Set<Object>";
            default -> "Object"; // Fallback raisonnable
        };
    }
}