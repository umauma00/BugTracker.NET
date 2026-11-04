package btnet.runtime.security;

/**
 * Stands for .NET {@code System.Security.Principal.IIdentity}:
 * {@code identity.Name} → {@code identity.Name()}, etc.
 */
public interface IIdentity {
    /** .NET {@code IIdentity.Name}. */
    String Name();

    /** .NET {@code IIdentity.IsAuthenticated}. */
    boolean IsAuthenticated();

    /** .NET {@code IIdentity.AuthenticationType}. */
    String AuthenticationType();
}
