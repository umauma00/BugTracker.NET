package btnet.runtime.security;

/**
 * Stands for .NET {@code System.Security.Claims.ClaimTypes} (same URI strings).
 */
public final class ClaimTypes {
    private ClaimTypes() {
    }

    public static final String Name = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name";
    public static final String NameIdentifier = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier";
    public static final String Email = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress";
    public static final String Role = "http://schemas.microsoft.com/ws/2008/06/identity/claims/role";
    public static final String GivenName = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname";
    public static final String Surname = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname";
    public static final String Sid = "http://schemas.microsoft.com/ws/2008/06/identity/claims/sid";
    public static final String AuthenticationMethod = "http://schemas.microsoft.com/ws/2008/06/identity/claims/authenticationmethod";
    public static final String UserData = "http://schemas.microsoft.com/ws/2008/06/identity/claims/userdata";
}
