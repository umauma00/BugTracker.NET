package judge.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Where in the ORIGINAL source this expectation comes from. Mandatory on every
 * scenario test method (enforced by {@code judge.JudgeSelfCheckTest}).
 *
 * <p>Because the original cannot be executed in the migration environment
 * (deviation D-001), every expected value is read out of the source. The
 * citation is how a reviewer checks it, and how a later run against the real
 * original can trace a mismatch back to the reading that produced it.
 *
 * <p>Format: {@code "src/BugTracker.Web/edit_bug.aspx.cs:412"} or a range
 * {@code ":412-430"}, path relative to the repository root.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(SourceRef.List.class)
public @interface SourceRef {
    String value();
    /** Optional note: what this line establishes. */
    String why() default "";

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface List { SourceRef[] value(); }
}
