package io.github.opencubicchunks.dasm.api.transform;

import io.github.opencubicchunks.dasm.api.Ref;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.CLASS)
public @interface TransformFrom {

    String value();

    Ref copyFrom() default @Ref();

    Signature signature() default @Signature(fromString = true);

    ApplicationStage stage() default ApplicationStage.PRE_APPLY;

    boolean makeSyntheticAccessor() default false;

    Class<?>[] redirectSets() default {};

    @Target({/* No targets allowed */})
    @Retention(RetentionPolicy.CLASS)
    @interface Signature {
        Class<?>[] args() default {};
        Class<?> ret() default void.class;
        boolean fromString() default false;
    }

    enum ApplicationStage {
        PRE_APPLY,
        POST_APPLY
    }
}
