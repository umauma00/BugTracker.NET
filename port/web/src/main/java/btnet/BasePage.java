// Source: src/BugTracker.Web/BasePage.cs
package btnet;

import java.util.ArrayList;
import java.util.List;

import btnet.Security.BtnetRoles;
import btnet.Security.PageAllowAnonymous;
import btnet.Security.PageAuthorizeAttribute;
import btnet.runtime.EventArgs;
import btnet.runtime.Strings;
import btnet.runtime.web.HttpUtility;
import btnet.runtime.web.Page;

@PageAuthorizeAttribute(BtnetRoles.User)
public class BasePage extends Page {
    @Override
    protected void OnPreInit(EventArgs e) {
        if (!IsUserAuthorized()) {
            // inventory: src/BugTracker.Web/BasePage.cs L16
            Response.Redirect(Strings.Format("default.aspx?returnUrl={0}", HttpUtility.UrlEncode(Request.RawUrl())));
        }
        super.OnPreInit(e);
    }

    private boolean IsUserAuthorized() {
        // inventory: src/BugTracker.Web/BasePage.cs L23
        boolean hasAnonymous = false;
        for (Class<?> c = getClass(); c != null; c = c.getSuperclass()) {
            if (c.getDeclaredAnnotationsByType(PageAllowAnonymous.class).length > 0) {
                hasAnonymous = true;
                break;
            }
        }
        if (hasAnonymous) {
            return true;
        }

        // inventory: src/BugTracker.Web/BasePage.cs L30
        List<PageAuthorizeAttribute> attributes = new ArrayList<>();
        for (Class<?> c = getClass(); c != null; c = c.getSuperclass()) {
            for (PageAuthorizeAttribute a : c.getDeclaredAnnotationsByType(PageAuthorizeAttribute.class)) {
                attributes.add(a);
            }
        }
        return Page.User.Identity().IsAuthenticated()
            && attributes.stream().allMatch(a -> new PageAuthorizeAttribute.Logic(a.value()).OnAuthorize(Page.User));
    }
}

// PORT STATUS: confidence=high todos=0
