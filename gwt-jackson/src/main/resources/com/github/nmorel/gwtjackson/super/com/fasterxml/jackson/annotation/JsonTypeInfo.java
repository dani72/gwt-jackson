package com.fasterxml.jackson.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Super source for {@link com.fasterxml.jackson.annotation.JsonTypeInfo} to remove the use of
 * {@link java.lang.String#format(String, Object...)} in the Value inner class.
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE,
    ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotation
public @interface JsonTypeInfo
{
    public enum Id {
        NONE(null),
        CLASS("@class"),
        MINIMAL_CLASS("@c"),
        NAME("@type"),
        SIMPLE_NAME("@type"),
        DEDUCTION(null),
        CUSTOM(null)
        ;

        private final String _defaultPropertyName;

        private Id(String defProp) {
            _defaultPropertyName = defProp;
        }

        public String getDefaultPropertyName() { return _defaultPropertyName; }
    }

    public enum As {
        PROPERTY,
        WRAPPER_OBJECT,
        WRAPPER_ARRAY,
        EXTERNAL_PROPERTY,
        EXISTING_PROPERTY
        ;
    }

    public Id use();

    public As include() default As.PROPERTY;

    public String property() default "";

    public Class<?> defaultImpl() default JsonTypeInfo.class;

    public boolean visible() default false;

    public OptBoolean requireTypeIdForSubtypes() default OptBoolean.DEFAULT;

    @Deprecated
    public abstract static class None {}
}
