package io.github.opencubicchunks.dasm.api.transform;

import io.github.opencubicchunks.dasm.api.Ref;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface TransformFromClass {
    Ref value();

    TransformFrom.ApplicationStage stage() default TransformFrom.ApplicationStage.PRE_APPLY;
}
