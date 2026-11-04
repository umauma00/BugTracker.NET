package btnet.runtime.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * .NET {@code System.ComponentModel.DataAnnotations.RequiredAttribute} on a model
 * property (RULEBOOK §2A JSON / Web API). Checked by {@link ApiBody#bind}: a
 * {@code null} value, or (unless {@link #AllowEmptyStrings()}) a string that is empty
 * or white space, adds the model error {@code "The <Field> field is required."}
 * under {@code "<param>.<Field>"}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Required {
    boolean AllowEmptyStrings() default false;

    /** Custom message; empty = the .NET default message. */
    String ErrorMessage() default "";
}
