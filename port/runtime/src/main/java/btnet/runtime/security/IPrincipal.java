package btnet.runtime.security;

/**
 * Stands for .NET {@code System.Security.Principal.IPrincipal} [J-C16]; the type
 * of the page/context {@code User} intrinsic.
 */
public interface IPrincipal {
    /** .NET {@code IPrincipal.Identity}. */
    IIdentity Identity();

    /** .NET {@code IPrincipal.IsInRole(role)}. */
    boolean IsInRole(String role);
}
