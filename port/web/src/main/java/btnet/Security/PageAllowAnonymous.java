// Source: src/BugTracker.Web/Security/PageAllowAnonymous.cs
package btnet.Security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// inventory: src/BugTracker.Web/BasePage.cs L23
// [AttributeUsage(AttributeTargets.Class, Inherited = true, AllowMultiple = false)]
// Inherited = true -> @Inherited; AllowMultiple = false -> not @Repeatable.
// BasePage walks every superclass anyway (RULEBOOK §2A GetCustomAttributes row).
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PageAllowAnonymous {
}

// PORT STATUS: confidence=high todos=0
