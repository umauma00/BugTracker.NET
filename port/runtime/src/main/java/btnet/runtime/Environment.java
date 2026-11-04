package btnet.runtime;

/** Stands for .NET {@code System.Environment} (the members the source uses). */
public final class Environment {
    private Environment() {
    }

    /** .NET {@code Environment.NewLine} on Windows: CRLF. */
    public static final String NewLine = "\r\n";

    /**
     * .NET {@code Environment.Version} as its {@code ToString()} text: the CLR version of .NET Framework 4.5+
     * ({@code admin.aspx:110}).
     */
    public static String Version() {
        return "4.0.30319.42000";
    }

    /** .NET {@code Environment.MachineName}. */
    public static String MachineName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (java.io.IOException e) {
            String h = System.getenv("HOSTNAME");
            return h != null ? h : "localhost";
        }
    }
}
