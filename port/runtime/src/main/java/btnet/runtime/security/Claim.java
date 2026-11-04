package btnet.runtime.security;

import btnet.runtime.ArgumentNullException;

import java.io.Serializable;

/**
 * Stands for .NET {@code System.Security.Claims.Claim}. {@code new Claim(type, value)};
 * properties follow the runtime convention: {@code c.Type()}, {@code c.Value()}.
 */
public final class Claim implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String type;
    private final String value;
    private final String valueType;
    private final String issuer;

    /** .NET {@code Claim(string type, string value)}; null type or value → {@code ArgumentNullException}. */
    public Claim(String type, String value) {
        this(type, value, "http://www.w3.org/2001/XMLSchema#string", "LOCAL AUTHORITY");
    }

    /** .NET {@code Claim(type, value, valueType, issuer)}. */
    public Claim(String type, String value, String valueType, String issuer) {
        if (type == null) {
            throw new ArgumentNullException("type");
        }
        if (value == null) {
            throw new ArgumentNullException("value");
        }
        this.type = type;
        this.value = value;
        this.valueType = valueType == null ? "http://www.w3.org/2001/XMLSchema#string" : valueType;
        this.issuer = issuer == null || issuer.isEmpty() ? "LOCAL AUTHORITY" : issuer;
    }

    public String Type() { return type; }

    public String Value() { return value; }

    public String ValueType() { return valueType; }

    public String Issuer() { return issuer; }

    /** .NET {@code Claim.ToString()}: {@code "<type>: <value>"}. */
    @Override
    public String toString() {
        return type + ": " + value;
    }
}
