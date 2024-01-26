package io.github.opencubicchunks.dasm.api.redirect;

import io.github.opencubicchunks.dasm.api.MethodSig;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface AddMethodToSets {
    Class<?> owner();

    MethodSig method();

    Class<?>[] sets();
}
