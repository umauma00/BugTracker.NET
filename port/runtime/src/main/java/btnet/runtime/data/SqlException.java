package btnet.runtime.data;

import btnet.runtime.DotNetException;

import java.sql.SQLException;

/**
 * .NET {@code System.Data.SqlClient.SqlException}: unchecked; {@code getMessage()} is the
 * server message text (several server errors joined with {@code "\r\n"}, as SqlClient does).
 * RULEBOOK §2A Exceptions.
 */
public class SqlException extends DotNetException {
    private static final long serialVersionUID = 1L;
    private final int number;

    public SqlException(String message) {
        super(message);
        this.number = 0;
    }

    public SqlException(SQLException cause) {
        super(joinMessages(cause), cause);
        this.number = cause.getErrorCode();
    }

    public SqlException(String message, Throwable cause) {
        super(message, cause);
        this.number = cause instanceof SQLException s ? s.getErrorCode() : 0;
    }

    /** .NET {@code SqlException.Number} (the server error number). */
    public int Number() {
        return number;
    }

    private static String joinMessages(SQLException e) {
        StringBuilder sb = new StringBuilder();
        SQLException cur = e;
        int guard = 0;
        while (cur != null && guard++ < 50) {
            String m = cur.getMessage();
            if (m != null) {
                if (sb.length() > 0) sb.append("\r\n");
                sb.append(m);
            }
            SQLException next = cur.getNextException();
            if (next == cur) break;
            cur = next;
        }
        return sb.toString();
    }
}
