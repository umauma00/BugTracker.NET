// Source: src/BugTracker.Web/Security/PageAuthorizeAttribute.cs
package btnet.Security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;

import btnet.runtime.security.IPrincipal;

// [AttributeUsage(AttributeTargets.Class, Inherited = true, AllowMultiple = true)]
// Inherited = true is honored by BasePage walking getClass() and every superclass (RULEBOOK §2A);
// @Inherited is deliberately not used.
// inventory: src/BugTracker.Web/Security/PageAuthorizeAttribute.cs L26
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(PageAuthorizeAttribute.PageAuthorizeAttributes.class)
public @interface PageAuthorizeAttribute {

    // params string[] roles; the empty constructor is Logic()
    String[] value() default {};

    // @Repeatable container (AllowMultiple = true)
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface PageAuthorizeAttributes {
        PageAuthorizeAttribute[] value();
    }

    // Logic of the C# attribute class (annotation instances cannot hold logic); RULEBOOK §2A [C28, J-C16]
    public static final class Logic {
        private final String[] _roles;

        public Logic() {
            _roles = new String[0];
        }

        public Logic(String... roles) {
            _roles = roles;
        }

        public boolean OnAuthorize(IPrincipal principal) {
            return principal.Identity().IsAuthenticated() &&
                (_roles.length == 0 || Arrays.stream(_roles).anyMatch(role -> principal.IsInRole(role)));
        }
    }
}
// PORT STATUS: confidence=high todos=0
