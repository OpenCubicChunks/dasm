package io.github.opencubicchunks.dasm.api.redirect;

import io.github.opencubicchunks.dasm.api.Ref;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** <pre>{@code}</pre>
 * <p>
 * Must be marked on any method within a class marked with {@link TypeRedirect}.<br/>
 * The signature of the method marked species the method to target within the {@link TypeRedirect#from()} class.<br/>
 * By convention marked methods should be {@code native}, but {@code abstract} or a stub definition (preferably that throws any {@link RuntimeException}) are equally valid.
 * </p><br/>
 * <h2>Example:</h2>
 * <h3>E1:</h3>
 * Specifies a {@code private String} method with the name {@code existingMethodName}, and the new name {@code newMethodName}
 * <pre>{@code
 *     @MethodRedirect("newMethodName")
 *     private native String existingMethodName();
 * }</pre><br/>
 * <h3>E2:</h3>
 * Specifies a {@code private String} method with the name {@code existingMethodName}, and the new name {@code newMethodName}.<br/>
 * Additionally an optional mappings owner is specified, see: {@link MethodRedirect#mappingsOwner()}.
 * <pre>{@code
 *     @MethodRedirect("newMethodName", mappingsOwner = @Ref(OtherClass.class))
 *     private native String existingMethodName();
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface MethodRedirect {
    /**
     * The new name for the method after the redirect is applied
     */
    String value();

    /**
     * Only useful if the codebase is remapped and methods owners are moved
     * Allows specifying the class which owns the method in
     */
    Ref mappingsOwner() default @Ref;
}
