package btnet.runtime.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@code @Page ValidateRequest="false"} directive attribute (RULEBOOK §2B B3).
 * A page class carrying {@code @ValidateRequest(false)} reads {@code Form},
 * {@code QueryString} and {@code Cookies} without request validation; every other
 * page validates ({@code Web.config:38}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface ValidateRequest {
    boolean value() default true;
}
