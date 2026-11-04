package btnet.runtime.data;

import btnet.runtime.ArgumentException;
import btnet.runtime.InvalidOperationException;

import javax.sql.DataSource;
import java.lang.ref.Cleaner;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * .NET {@code System.Data.SqlClient.SqlConnection} over JDBC (RULEBOOK §2A Database, §3 [S1-9]).
 * <ul>
 * <li><b>port/web</b>: {@code BtnetApplication} calls {@link #UseDataSource(DataSource)} once.
 *   A non-{@code jdbc:} .NET connection string is then ignored and the constructor borrows a
 *   connection from the Spring (HikariCP) DataSource; a {@code jdbc:} string is used as-is.
 *   {@link #Open()} right after construction is a no-op.</li>
 * <li><b>port/service</b>: no DataSource; the SqlClient string is converted to a
 *   {@code jdbc:sqlserver:} URL (unknown keyword → {@code ArgumentException("Keyword not
 *   supported: '<keyword>'.")}) and {@link #Open()} opens one <b>unpooled</b> connection via
 *   {@link DriverManager}.</li>
 * </ul>
 * In both modes a {@link Cleaner} closes the JDBC connection once this object becomes
 * unreachable without {@code Close()} (as SqlClient reclaims the pooled connection of a
 * collected owner), so the source's leaks are reproduced without exhausting anything.
 * {@code Close()}/{@code close()}/{@code Dispose()} are idempotent; a closed connection can be
 * re-opened, as in .NET.
 */
public class SqlConnection implements AutoCloseable {

    /** .NET {@code System.Data.ConnectionState} (the two states the port distinguishes). */
    public enum ConnectionState { Closed, Open }

    private static volatile DataSource dataSource;
    private static final Cleaner CLEANER = Cleaner.create();

    /** Called once by {@code BtnetApplication} (port/web). */
    public static void UseDataSource(DataSource ds) {
        dataSource = ds;
    }

    /** The registered DataSource, or {@code null} (port/service). */
    public static DataSource registeredDataSource() {
        return dataSource;
    }

    /** Holds only the JDBC connection, so the Cleaner action never references the owner. */
    private static final class Holder implements Runnable {
        volatile Connection connection;

        @Override
        public void run() {
            Connection c = connection;
            connection = null;
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException ignored) {
                    // reclaiming a leaked connection never throws
                }
            }
        }
    }

    private final String connectionString;
    private final String jdbcUrl;          // null → borrow from the DataSource
    private final Holder holder = new Holder();
    private boolean openedByConstructor;
    SqlDataReader activeReader;

    public SqlConnection() {
        this.connectionString = "";
        this.jdbcUrl = null;
        CLEANER.register(this, holder);
    }

    public SqlConnection(String connectionString) {
        this.connectionString = connectionString == null ? "" : connectionString;
        CLEANER.register(this, holder);
        if (this.connectionString.regionMatches(true, 0, "jdbc:", 0, 5)) {
            this.jdbcUrl = this.connectionString;
        } else if (dataSource != null) {
            this.jdbcUrl = null;
        } else {
            this.jdbcUrl = this.connectionString.isBlank() ? null : toJdbcUrl(this.connectionString);
        }
        if (dataSource != null) {
            openPhysical();
            openedByConstructor = true;
        }
    }

    public String ConnectionString() {
        return connectionString;
    }

    public ConnectionState State() {
        return holder.connection != null ? ConnectionState.Open : ConnectionState.Closed;
    }

    public void Open() {
        if (holder.connection != null) {
            if (openedByConstructor) {
                openedByConstructor = false;   // RULEBOOK §2A: Open() is a no-op after construction (web)
                return;
            }
            throw new InvalidOperationException("The connection was not closed. The connection's current state is open.");
        }
        openPhysical();
    }

    private void openPhysical() {
        try {
            if (jdbcUrl != null) {
                holder.connection = DriverManager.getConnection(jdbcUrl);
            } else if (dataSource != null) {
                holder.connection = dataSource.getConnection();
            } else {
                throw new InvalidOperationException("The ConnectionString property has not been initialized.");
            }
        } catch (SQLException e) {
            throw new SqlException(e);
        }
    }

    public void Close() {
        openedByConstructor = false;
        SqlDataReader r = activeReader;
        activeReader = null;
        if (r != null) {
            r.connectionClosing();
        }
        holder.run();
    }

    public void Dispose() {
        Close();
    }

    @Override
    public void close() {
        Close();
    }

    /** The open JDBC connection, or the .NET "requires an open and available Connection" error. */
    Connection jdbc(String operation) {
        Connection c = holder.connection;
        if (c == null) {
            throw new InvalidOperationException(operation
                    + " requires an open and available Connection. The connection's current state is closed.");
        }
        return c;
    }

    boolean isOpen() {
        return holder.connection != null;
    }

    // ------------------------------------------------------------------ connection-string conversion

    private static final Set<String> IGNORED = Set.of(
            "pooling", "max pool size", "min pool size", "connection lifetime", "multipleactiveresultsets",
            "persist security info", "persistsecurityinfo", "workstation id", "wsid", "packet size", "enlist",
            "connection reset", "load balance timeout", "asynchronous processing", "async", "user instance",
            "current language", "language", "failover partner", "network library", "net", "network",
            "replication", "transaction binding", "type system version", "context connection",
            "applicationintent", "application intent", "multisubnetfailover", "connectretrycount",
            "connectretryinterval", "transparentnetworkipresolution", "column encryption setting");

    /**
     * Parses a SqlClient connection string ({@code ;}-separated {@code key=value}, keys trimmed
     * and case-insensitive, values optionally quoted) into a {@code jdbc:sqlserver:} URL
     * (RULEBOOK §3 [S1-9]).
     */
    public static String toJdbcUrl(String cs) {
        Map<String, String> kv = parse(cs);
        String host = null, instance = null, port = null;
        StringBuilder props = new StringBuilder();
        boolean encryptSeen = false, timeoutSeen = false;
        for (Map.Entry<String, String> e : kv.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            switch (k) {
                case "server", "data source", "address", "addr", "network address" -> {
                    String h = v.trim();
                    if (h.regionMatches(true, 0, "tcp:", 0, 4)) h = h.substring(4).trim();
                    int comma = h.indexOf(',');
                    if (comma >= 0) {
                        port = h.substring(comma + 1).trim();
                        h = h.substring(0, comma).trim();
                    }
                    int bs = h.indexOf('\\');
                    if (bs >= 0) {
                        instance = h.substring(bs + 1).trim();
                        h = h.substring(0, bs).trim();
                    }
                    if (h.equalsIgnoreCase("(local)") || h.equals(".")) h = "localhost";
                    // (localdb) is not reachable by mssql-jdbc: it fails at Open() like an unreachable server
                    host = h;
                }
                case "database", "initial catalog" -> appendProp(props, "databaseName", v);
                case "user id", "uid", "user" -> appendProp(props, "user", v);
                case "password", "pwd" -> appendProp(props, "password", v);
                case "integrated security", "trusted_connection" -> {
                    String b = v.trim().toLowerCase(Locale.ROOT);
                    if (b.equals("true") || b.equals("yes") || b.equals("sspi")) {
                        // TODO(port): integrated security needs Kerberos/native auth off Windows
                        appendProp(props, "integratedSecurity", "true");
                    }
                }
                case "encrypt" -> {
                    encryptSeen = true;
                    appendProp(props, "encrypt", bool(v));
                }
                case "trustservercertificate", "trust server certificate" ->
                        appendProp(props, "trustServerCertificate", bool(v));
                case "connect timeout", "connection timeout", "timeout" -> {
                    timeoutSeen = true;
                    appendProp(props, "loginTimeout", v.trim());
                }
                case "application name", "app" -> appendProp(props, "applicationName", v);
                default -> {
                    if (!IGNORED.contains(k)) {
                        throw new ArgumentException("Keyword not supported: '" + k + "'.");
                    }
                }
            }
        }
        if (!encryptSeen) appendProp(props, "encrypt", "false");      // SqlClient 4.5 default
        if (!timeoutSeen) appendProp(props, "loginTimeout", "15");
        StringBuilder url = new StringBuilder("jdbc:sqlserver://");
        if (host != null && !host.isEmpty()) url.append(host);
        if (instance != null && !instance.isEmpty()) url.append('\\').append(instance);
        if (port != null && !port.isEmpty()) url.append(':').append(port);
        url.append(props);
        return url.toString();
    }

    private static String bool(String v) {
        String b = v.trim().toLowerCase(Locale.ROOT);
        return (b.equals("true") || b.equals("yes") || b.equals("mandatory") || b.equals("strict")) ? "true" : "false";
    }

    private static void appendProp(StringBuilder sb, String name, String value) {
        sb.append(';').append(name).append('=');
        boolean needsBraces = value.indexOf(';') >= 0 || value.indexOf('{') >= 0 || value.indexOf('}') >= 0
                || value.indexOf('=') >= 0 || !value.equals(value.trim());
        if (needsBraces) {
            sb.append('{').append(value.replace("}", "}}")).append('}');
        } else {
            sb.append(value);
        }
    }

    /** SqlClient connection-string tokenizer; later duplicates win, as in SqlClient. */
    static Map<String, String> parse(String cs) {
        Map<String, String> result = new LinkedHashMap<>();
        int i = 0, n = cs.length();
        while (i < n) {
            while (i < n && (cs.charAt(i) == ';' || Character.isWhitespace(cs.charAt(i)))) i++;
            if (i >= n) break;
            int eq = cs.indexOf('=', i);
            if (eq < 0) {
                throw new ArgumentException("Format of the initialization string does not conform to specification starting at index " + i + ".");
            }
            String key = cs.substring(i, eq).trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            i = eq + 1;
            while (i < n && Character.isWhitespace(cs.charAt(i)) && cs.charAt(i) != ';') i++;
            String value;
            if (i < n && (cs.charAt(i) == '\'' || cs.charAt(i) == '"')) {
                char q = cs.charAt(i++);
                StringBuilder sb = new StringBuilder();
                while (i < n) {
                    char c = cs.charAt(i);
                    if (c == q) {
                        if (i + 1 < n && cs.charAt(i + 1) == q) {
                            sb.append(q);
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    sb.append(c);
                    i++;
                }
                value = sb.toString();
                while (i < n && cs.charAt(i) != ';') i++;
            } else {
                int semi = cs.indexOf(';', i);
                if (semi < 0) semi = n;
                value = cs.substring(i, semi).trim();
                i = semi;
            }
            if (key.isEmpty()) {
                throw new ArgumentException("Format of the initialization string does not conform to specification starting at index 0.");
            }
            result.put(key, value);
        }
        return result;
    }
}
