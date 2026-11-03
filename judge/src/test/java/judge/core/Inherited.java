package judge.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an expectation that pins behavior the original has but that is
 * probably a defect (a {@code BUG(port)} candidate). The port must reproduce it
 * until parity is signed off; prompt 06 may then fix it as its own flagged
 * change, updating this test in the same change. The judge never weakens an
 * expectation because it looks wrong.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Inherited {
    /** Short description of the suspected defect. */
    String value();
}
