package io.github.opencubicchunks.dasm.api;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({/* No targets allowed */})
@Retention(RetentionPolicy.CLASS)
public @interface MethodSig {
    @Deprecated String value() default "";

    Class<?> ret() default void.class;
    String name() default "";
    Class<?>[] args() default {};
}
