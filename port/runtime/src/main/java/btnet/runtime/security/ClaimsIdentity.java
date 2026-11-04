package btnet.runtime.security;

import btnet.runtime.ArgumentNullException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Stands for .NET {@code System.Security.Claims.ClaimsIdentity} [J-C3].
 *
 * <p>Claims are an ordered list in insertion order, duplicates kept (never a
 * map). {@link #FindFirst(String)} returns the first match in insertion order;
 * {@link #HasClaim(String, String)} matches any claim. Claim types compare
 * ordinal-ignore-case, values ordinal (.NET). {@link #IsAuthenticated()} =
 * non-empty {@code AuthenticationType}; {@link #Name()} = value of the first
 * claim of {@code NameClaimType}, else {@code null}.
 */
public class ClaimsIdentity implements IIdentity, Serializable {
    private static final long serialVersionUID = 1L;

    /** .NET {@code ClaimsIdentity.DefaultNameClaimType}. */
    public static final String DefaultNameClaimType = ClaimTypes.Name;
    /** .NET {@code ClaimsIdentity.DefaultRoleClaimType}. */
    public static final String DefaultRoleClaimType = ClaimTypes.Role;

    private final List<Claim> claims = new ArrayList<>();
    private final String authenticationType;
    private final String nameClaimType;
    private final String roleClaimType;

    public ClaimsIdentity() {
        this((Iterable<Claim>) null, null, null, null);
    }

    public ClaimsIdentity(String authenticationType) {
        this((Iterable<Claim>) null, authenticationType, null, null);
    }

    public ClaimsIdentity(Iterable<Claim> claims) {
        this(claims, null, null, null);
    }

    public ClaimsIdentity(Iterable<Claim> claims, String authenticationType) {
        this(claims, authenticationType, null, null);
    }

    /** .NET {@code ClaimsIdentity(claims, authenticationType, nameType, roleType)}; null/empty types → defaults. */
    public ClaimsIdentity(Iterable<Claim> claims, String authenticationType, String nameType, String roleType) {
        this.authenticationType = authenticationType;
        this.nameClaimType = nameType == null || nameType.isEmpty() ? DefaultNameClaimType : nameType;
        this.roleClaimType = roleType == null || roleType.isEmpty() ? DefaultRoleClaimType : roleType;
        if (claims != null) {
            for (Claim c : claims) {
                AddClaim(c);
            }
        }
    }

    /** .NET {@code AddClaim}; null → {@code ArgumentNullException}. */
    public void AddClaim(Claim claim) {
        if (claim == null) {
            throw new ArgumentNullException("Value cannot be null.\r\nParameter name: claim");
        }
        claims.add(claim);
    }

    /** .NET {@code AddClaims}. */
    public void AddClaims(Iterable<Claim> cs) {
        if (cs == null) {
            throw new ArgumentNullException("Value cannot be null.\r\nParameter name: claims");
        }
        for (Claim c : cs) {
            if (c != null) {
                AddClaim(c);
            }
        }
    }

    /** .NET {@code Claims} (read-only view, insertion order). */
    public List<Claim> Claims() {
        return Collections.unmodifiableList(claims);
    }

    /** .NET {@code FindFirst(type)}: first claim (insertion order) whose type matches ignoring case, else null. */
    public Claim FindFirst(String type) {
        if (type == null) {
            throw new ArgumentNullException("Value cannot be null.\r\nParameter name: type");
        }
        for (Claim c : claims) {
            if (c.Type().equalsIgnoreCase(type)) {
                return c;
            }
        }
        return null;
    }

    /** .NET {@code FindFirst(Predicate&lt;Claim&gt;)}. */
    public Claim FindFirst(Predicate<Claim> match) {
        for (Claim c : claims) {
            if (match.test(c)) {
                return c;
            }
        }
        return null;
    }

    /** .NET {@code FindAll(type)}. */
    public List<Claim> FindAll(String type) {
        if (type == null) {
            throw new ArgumentNullException("Value cannot be null.\r\nParameter name: type");
        }
        List<Claim> r = new ArrayList<>();
        for (Claim c : claims) {
            if (c.Type().equalsIgnoreCase(type)) {
                r.add(c);
            }
        }
        return r;
    }

    /** .NET {@code HasClaim(type, value)}: any claim with matching type (ignore case) and value (ordinal). */
    public boolean HasClaim(String type, String value) {
        if (type == null) {
            throw new ArgumentNullException("Value cannot be null.\r\nParameter name: type");
        }
        if (value == null) {
            throw new ArgumentNullException("Value cannot be null.\r\nParameter name: value");
        }
        for (Claim c : claims) {
            if (c.Type().equalsIgnoreCase(type) && c.Value().equals(value)) {
                return true;
            }
        }
        return false;
    }

    /** .NET {@code HasClaim(Predicate&lt;Claim&gt;)}. */
    public boolean HasClaim(Predicate<Claim> match) {
        return FindFirst(match) != null;
    }

    /** .NET {@code RemoveClaim}. */
    public void RemoveClaim(Claim claim) {
        claims.remove(claim);
    }

    @Override
    public String Name() {
        Claim c = FindFirst(nameClaimType);
        return c == null ? null : c.Value();
    }

    @Override
    public boolean IsAuthenticated() {
        return authenticationType != null && !authenticationType.isEmpty();
    }

    @Override
    public String AuthenticationType() {
        return authenticationType;
    }

    public String NameClaimType() {
        return nameClaimType;
    }

    public String RoleClaimType() {
        return roleClaimType;
    }
}
