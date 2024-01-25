package io.github.opencubicchunks.dasm.api.transform;

import io.github.opencubicchunks.dasm.api.MethodSig;
import io.github.opencubicchunks.dasm.api.Ref;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.CLASS)
public @interface TransformFrom {
    MethodSig value();

    ApplicationStage stage() default ApplicationStage.PRE_APPLY;

    boolean makeSyntheticAccessor() default false;

    Ref copyFrom() default @Ref();

    Class<?>[] redirectSets() default { };

    Class<?>[] addToSets() default { };

    enum ApplicationStage {
        PRE_APPLY,
        POST_APPLY
    }
}
