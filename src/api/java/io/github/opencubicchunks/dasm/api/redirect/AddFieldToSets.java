package io.github.opencubicchunks.dasm.api.redirect;

import io.github.opencubicchunks.dasm.api.FieldSig;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface AddFieldToSets {
    Class<?> owner();

    FieldSig field();

    Class<?>[] sets();
}
