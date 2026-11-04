package btnet.runtime;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * .NET {@code System.Exception} members over Java throwables (RULEBOOK §2A Exceptions, §3 C14/C30/S1-10/S1-22):
 * {@code e.Message}, {@code e.StackTrace}, {@code e.GetBaseException()}, {@code e.ToString()}, checked-exception
 * wrapping and the bare-catch rethrow of page control flow.
 */
public final class Exceptions {
    private Exceptions() {
    }

    private static final Map<String, String> NET_NAMES = Map.ofEntries(
            Map.entry("btnet.runtime.DotNetException", "System.Exception"),
            Map.entry("btnet.runtime.FormatException", "System.FormatException"),
            Map.entry("btnet.runtime.OverflowException", "System.OverflowException"),
            Map.entry("btnet.runtime.InvalidCastException", "System.InvalidCastException"),
            Map.entry("java.lang.ClassCastException", "System.InvalidCastException"),
            Map.entry("btnet.runtime.ArgumentException", "System.ArgumentException"),
            Map.entry("btnet.runtime.ArgumentNullException", "System.ArgumentNullException"),
            Map.entry("btnet.runtime.ArgumentOutOfRangeException", "System.ArgumentOutOfRangeException"),
            Map.entry("btnet.runtime.IndexOutOfRangeException", "System.IndexOutOfRangeException"),
            Map.entry("java.lang.ArrayIndexOutOfBoundsException", "System.IndexOutOfRangeException"),
            Map.entry("btnet.runtime.KeyNotFoundException", "System.Collections.Generic.KeyNotFoundException"),
            Map.entry("btnet.runtime.ConfigurationErrorsException",
                    "System.Configuration.ConfigurationErrorsException"),
            Map.entry("btnet.runtime.SecurityException", "System.Security.SecurityException"),
            Map.entry("btnet.runtime.FileNotFoundException", "System.IO.FileNotFoundException"),
            Map.entry("btnet.runtime.DirectoryNotFoundException", "System.IO.DirectoryNotFoundException"),
            Map.entry("btnet.runtime.IOException", "System.IO.IOException"),
            Map.entry("btnet.runtime.InvalidOperationException", "System.InvalidOperationException"),
            Map.entry("btnet.runtime.UriFormatException", "System.UriFormatException"),
            Map.entry("btnet.runtime.TaskCanceledException", "System.Threading.Tasks.TaskCanceledException"),
            Map.entry("btnet.runtime.Win32Exception", "System.ComponentModel.Win32Exception"),
            Map.entry("btnet.runtime.NotSupportedException", "System.NotSupportedException"),
            Map.entry("java.lang.NullPointerException", "System.NullReferenceException"),
            Map.entry("java.lang.ArithmeticException", "System.DivideByZeroException"),
            Map.entry("java.lang.UnsupportedOperationException", "System.NotSupportedException"),
            Map.entry("java.lang.OutOfMemoryError", "System.OutOfMemoryException"),
            Map.entry("java.lang.StackOverflowError", "System.StackOverflowException"),
            Map.entry("btnet.runtime.data.SqlException", "System.Data.SqlClient.SqlException"),
            Map.entry("btnet.runtime.data.DuplicateNameException", "System.Data.DuplicateNameException"),
            Map.entry("btnet.runtime.net.HttpRequestException", "System.Net.Http.HttpRequestException"),
            Map.entry("btnet.runtime.web.HttpException", "System.Web.HttpException"),
            Map.entry("btnet.runtime.web.HttpRequestValidationException", "System.Web.HttpRequestValidationException"),
            Map.entry("btnet.runtime.web.ResponseEndException", "System.Threading.ThreadAbortException"),
            Map.entry("btnet.runtime.web.RedirectException", "System.Threading.ThreadAbortException"),
            Map.entry("btnet.runtime.web.TransferException", "System.Threading.ThreadAbortException"));

    /** The .NET full type name a throwable stands for (fixed table; unknown → Java FQN). */
    public static String dotNetTypeName(Throwable e) {
        String n = e.getClass().getName();
        String m = NET_NAMES.get(n);
        if (m != null) {
            return m;
        }
        if (n.startsWith("btnet.runtime.mail.pop.") && n.endsWith("Exception")) {
            return "OpenPop.Pop3.Exceptions." + e.getClass().getSimpleName();
        }
        return n;
    }

    /**
     * .NET {@code e.Message} [S1-10]: {@code getMessage()}, except NPE → "Object reference not set to an instance
     * of an object.", plain ClassCastException → "Specified cast is not valid.", ArithmeticException →
     * "Attempted to divide by zero.", ArrayIndexOutOfBoundsException → "Index was outside the bounds of the
     * array."; a null message → "Exception of type '&lt;.NET name&gt;' was thrown.". {@code e == null} → NPE.
     */
    public static String Message(Throwable e) {
        if (e == null) {
            throw new NullPointerException();
        }
        if (e instanceof NullPointerException) {
            return "Object reference not set to an instance of an object.";
        }
        if (e.getClass() == ClassCastException.class) {
            return "Specified cast is not valid.";
        }
        if (e instanceof ArithmeticException) {
            return "Attempted to divide by zero.";
        }
        if (e instanceof ArrayIndexOutOfBoundsException) {
            return "Index was outside the bounds of the array.";
        }
        String m = e.getMessage();
        if (m == null) {
            return "Exception of type '" + dotNetTypeName(e) + "' was thrown.";
        }
        return m;
    }

    /** .NET {@code e.StackTrace}: {@code "   at <frame>"} lines joined by CRLF; {@code null} when there are no frames. */
    public static String StackTrace(Throwable e) {
        StackTraceElement[] st = e.getStackTrace();
        if (st == null || st.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < st.length; i++) {
            if (i > 0) {
                sb.append("\r\n");
            }
            sb.append("   at ").append(st[i]);
        }
        return sb.toString();
    }

    /** .NET {@code e.GetBaseException()} [C14]: innermost non-null cause, {@code e} if none. */
    public static Throwable baseOf(Throwable e) {
        Map<Throwable, Boolean> seen = new IdentityHashMap<>();
        Throwable t = e;
        while (t.getCause() != null && seen.put(t, Boolean.TRUE) == null) {
            t = t.getCause();
        }
        return t;
    }

    /**
     * .NET {@code e.ToString()} [C14]: {@code <.NET type>: <Message>}, then {@code " ---> " + inner.ToString() +
     * "\r\n   --- End of inner exception stack trace ---"} for a cause, then CRLF and the stack text.
     */
    public static String dotNetToString(Throwable e) {
        return toStringDepth(e, 0);
    }

    private static String toStringDepth(Throwable e, int depth) {
        StringBuilder sb = new StringBuilder(dotNetTypeName(e));
        String m = Message(e);
        if (m != null && !m.isEmpty()) {
            sb.append(": ").append(m);
        }
        Throwable cause = e.getCause();
        if (cause != null && cause != e && depth < 32) {
            sb.append(" ---> ").append(toStringDepth(cause, depth + 1))
                    .append("\r\n   --- End of inner exception stack trace ---");
        }
        String st = StackTrace(e);
        if (st != null) {
            sb.append("\r\n").append(st);
        }
        return sb.toString();
    }

    /**
     * Wraps a checked JDK exception unchecked [S1-22]: RuntimeException → itself; Error → rethrown;
     * InterruptedException → re-sets the interrupt flag, then {@link DotNetException};
     * {@code java.sql.SQLException} → {@code btnet.runtime.data.SqlException}; {@code java.io.FileNotFoundException}
     * / {@code NoSuchFileException} → runtime {@link FileNotFoundException}; other {@code java.io.IOException} →
     * runtime {@link IOException}; anything else → {@link DotNetException} with the same message and cause.
     */
    public static RuntimeException wrap(Throwable e) {
        if (e instanceof RuntimeException r) {
            return r;
        }
        if (e instanceof Error err) {
            throw err;
        }
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return new DotNetException("Thread was interrupted from a waiting state.", e);
        }
        if (e instanceof java.sql.SQLException sql) {
            return new btnet.runtime.data.SqlException(sql);
        }
        if (e instanceof java.io.FileNotFoundException || e instanceof java.nio.file.NoSuchFileException) {
            return new FileNotFoundException(e.getMessage(), e);
        }
        if (e instanceof java.io.UncheckedIOException u) {
            return wrap(u.getCause());
        }
        if (e instanceof java.io.IOException) {
            return new IOException(e.getMessage(), e);
        }
        return new DotNetException(e.getMessage(), e);
    }

    /**
     * Bare-catch guard [C30]: rethrows page control flow ({@code ResponseEndException} and its Redirect/Transfer
     * subclasses) so a bare catch never swallows it.
     */
    public static void rethrowIfEnd(Throwable t) {
        if (t instanceof btnet.runtime.web.ResponseEndException r) {
            throw r;
        }
    }
}
