package io.github.opencubicchunks.dasm.api.redirect;

import io.github.opencubicchunks.dasm.api.Ref;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** <pre>{@code}</pre>
 * Must be marked on any class within a redirect set {@code interface}.<br/>
 * Specifies that a type should be redirected to another type using {@link TypeRedirect#from()} and {@link TypeRedirect#to()}.
 * <p/>
 * The class marked with {@link TypeRedirect} can optionally contain any number of field redirects (see: {@link FieldRedirect}) or method redirects (see: {@link MethodRedirect})
 * <p/>
 * By convention the marked class's name should have the format {@code (FromClass)To(ToClass)Redirects}, eg: {@code ObjectToStringRedirects}.
 * It should also be {@code abstract}
 * <p/>
 * <h2>Example:</h2>
 * Specifies that {@code Object} should be redirected to {@code SomePrivateClass} (referred to by string, as it's private)
 * <pre>{@code
 *     @TypeRedirect(from = @Ref(Object.class), to = @Ref(string = "java.lang.SomePrivateClass"))
 *     abstract class ObjectToSomePrivateClassRedirects {
 *         // optionally field and method redirects here
 *     }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface TypeRedirect {
    Ref from();

    Ref to();
}
