package btnet.runtime.security;

import btnet.runtime.ArgumentNullException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stands for .NET {@code System.Security.Claims.ClaimsPrincipal} [J-C16]
 * ({@code implements IPrincipal}). {@link #IsInRole(String)} matches <b>any</b>
 * claim of each identity's {@code RoleClaimType} [J-C3].
 *
 * <p>{@link #Current()} is the principal of the current request (Spring Security
 * context, B7); without a signed-in identity it is {@link #Anonymous()} — the
 * ASP.NET anonymous user: identity not authenticated, {@code Name()} {@code ""}
 * (a {@code GenericIdentity("")}, which in .NET 4.5 is a {@code ClaimsIdentity}
 * holding one empty name claim).
 */
public class ClaimsPrincipal implements IPrincipal, Serializable {
    private static final long serialVersionUID = 1L;

    private final List<ClaimsIdentity> identities = new ArrayList<>();

    public ClaimsPrincipal() {
    }

    public ClaimsPrincipal(ClaimsIdentity identity) {
        if (identity == null) {
            throw new ArgumentNullException("identity");
        }
        identities.add(identity);
    }

    /** .NET {@code ClaimsPrincipal(IIdentity)}: a non-claims identity is wrapped by name/type. */
    public ClaimsPrincipal(IIdentity identity) {
        if (identity == null) {
            throw new ArgumentNullException("identity");
        }
        if (identity instanceof ClaimsIdentity ci) {
            identities.add(ci);
        } else {
            List<Claim> cs = new ArrayList<>();
            if (identity.Name() != null) {
                cs.add(new Claim(ClaimTypes.Name, identity.Name()));
            }
            identities.add(new ClaimsIdentity(cs, identity.AuthenticationType()));
        }
    }

    public ClaimsPrincipal(Iterable<ClaimsIdentity> ids) {
        if (ids == null) {
            throw new ArgumentNullException("identities");
        }
        for (ClaimsIdentity i : ids) {
            identities.add(i);
        }
    }

    /** The ASP.NET anonymous user (see class comment). */
    public static ClaimsPrincipal Anonymous() {
        return new ClaimsPrincipal(new ClaimsIdentity(List.of(new Claim(ClaimTypes.Name, "")), ""));
    }

    /**
     * The current request's principal (.NET {@code HttpContext.User} /
     * {@code ClaimsPrincipal.Current}): the identity signed in through
     * {@link AuthenticationManager#SignIn}, else {@link #Anonymous()}.
     */
    public static ClaimsPrincipal Current() {
        SecurityContext ctx = SecurityContextHolder.getContext();
        Authentication a = ctx == null ? null : ctx.getAuthentication();
        if (a != null && a.getPrincipal() instanceof ClaimsPrincipal p) {
            return p;
        }
        return Anonymous();
    }

    /** .NET {@code Identities}. */
    public List<ClaimsIdentity> Identities() {
        return Collections.unmodifiableList(identities);
    }

    /** .NET {@code AddIdentity}. */
    public void AddIdentity(ClaimsIdentity identity) {
        if (identity == null) {
            throw new ArgumentNullException("identity");
        }
        identities.add(identity);
    }

    /** .NET {@code Identity}: the first identity, {@code null} if none. */
    @Override
    public IIdentity Identity() {
        return identities.isEmpty() ? null : identities.get(0);
    }

    @Override
    public boolean IsInRole(String role) {
        for (ClaimsIdentity i : identities) {
            if (i != null && role != null && i.HasClaim(i.RoleClaimType(), role)) {
                return true;
            }
        }
        return false;
    }

    /** .NET {@code Claims}: every claim of every identity, in order. */
    public List<Claim> Claims() {
        List<Claim> r = new ArrayList<>();
        for (ClaimsIdentity i : identities) {
            r.addAll(i.Claims());
        }
        return r;
    }

    /** .NET {@code FindFirst(type)} across identities. */
    public Claim FindFirst(String type) {
        for (ClaimsIdentity i : identities) {
            Claim c = i.FindFirst(type);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    /** .NET {@code HasClaim(type, value)} across identities. */
    public boolean HasClaim(String type, String value) {
        for (ClaimsIdentity i : identities) {
            if (i.HasClaim(type, value)) {
                return true;
            }
        }
        return false;
    }
}
