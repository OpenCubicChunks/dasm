package io.github.opencubicchunks.dasm.api.redirect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** <pre>{@code}</pre>
 * Marks that an {@code interface} should be used as a redirect set.
 * <p/>
 * The marked type must be an {@code interface} and must contain only {@code abstract class} definitions marked with {@link TypeRedirect}<br/>
 * By convention the marked type's name should end with `Set`
 * <p/>
 * <h2>Example</h2>
 * <pre>{@code
 * @DasmRedirectSet
 * interface ExampleSet {
 *
 *     @TypeRedirect(from = @Ref(Foo.class), to = @Ref(Bar.class))
 *     abstract class FooToBarRedirects {
 *
 *         @FieldRedirect("newName") public String stringThing;
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface DasmRedirectSet {
}
