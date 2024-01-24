package io.github.opencubicchunks.dasm.api.redirect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** <pre>{@code}</pre>
 * Must be marked on any field within a class marked with {@link TypeRedirect}.<br/>
 * The type and name of the field marked specify the field to target within the {@link TypeRedirect#from()} class.
 * <br/><br/>
 * The {@code final} keyword may be safely omitted to avoid having to define a constructor to set it.
 * <p/>
 * <h2>Example:</h2>
 * The following field redirect specifies a {@code private String} field with the name {@code existingFieldName}, and the new name {@code newFieldName}
 * <pre>{@code
 *     @FieldRedirect("newFieldName")
 *     private String existingFieldName;
 * }</pre>
 * <p/>
 * The field redirect itself is built up from {@link TypeRedirect#from()} and {@link TypeRedirect#to()} on the enclosing class
 * as well as information from the field it's marking.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface FieldRedirect {
    /**
     * The new name for the field after the redirect is applied
     */
    String value();
}
