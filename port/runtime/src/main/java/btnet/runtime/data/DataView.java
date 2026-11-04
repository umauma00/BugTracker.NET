package btnet.runtime.data;

import btnet.runtime.ArgumentException;
import btnet.runtime.Convert;
import btnet.runtime.CultureInfo;
import btnet.runtime.DBNull;
import btnet.runtime.DateTimes;
import btnet.runtime.DotNetException;
import btnet.runtime.IndexOutOfRangeException;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * .NET {@code System.Data.DataView}: a sorted, filtered view over a {@link DataTable}
 * ({@code dv.Count()}, {@code dv.get(i)}, {@code dv.Table()}, {@code dv.Sort(s)},
 * {@code dv.RowFilter(s)}, {@code dv.Find(k)}; iterable as {@link DataRowView}s). The view
 * follows later changes of the table (rebuilt lazily).
 * <p>
 * <b>Sort</b> — .NET {@code DataTable.ParseSortString}: comma-separated entries
 * {@code col}, {@code [col]}, {@code col ASC|DESC}; an unknown column →
 * {@code IndexOutOfRangeException("Cannot find column <x>.")}; an unbalanced {@code [} →
 * {@code ArgumentException("<entry> isn't a valid Sort string entry.")}. Strings compare
 * culture-sensitively (en-US) and case-insensitively, trailing spaces ignored (the default
 * {@code DataTable.CaseSensitive = false}); DBNull sorts first; ties keep table order.
 * <p>
 * <b>RowFilter</b> — only the expression forms found in the source: column references
 * ({@code name}, {@code [name]}), string/number/{@code true}/{@code false}/{@code null}
 * literals, {@code = <> < > <= >=}, {@code [NOT] IN (…)}, {@code AND}, {@code OR}, {@code NOT},
 * parentheses. Comparisons with DBNull are unknown (row excluded); a string operand compared
 * with a typed one is converted to that type. Anything else →
 * {@code UnsupportedOperationException("TODO(port) RowFilter")}. The filter is parsed and
 * evaluated over every row when set; on any exception the previous filter stays in effect.
 * <p>
 * <b>Find</b> without a Sort → {@code ArgumentException("Find finds a row based on a Sort order,
 * and no Sort order is specified.")} (RULEBOOK §2A, edit_user.aspx.cs:233).
 */
public class DataView implements Iterable<DataRowView> {

    /** .NET {@code System.Data.SyntaxErrorException}. */
    public static class SyntaxErrorException extends DotNetException {
        private static final long serialVersionUID = 1L;

        public SyntaxErrorException(String message) {
            super(message);
        }
    }

    /** .NET {@code System.Data.EvaluateException}. */
    public static class EvaluateException extends DotNetException {
        private static final long serialVersionUID = 1L;

        public EvaluateException(String message) {
            super(message);
        }
    }

    private DataTable table;
    private String sort = "";
    private String rowFilter = "";
    private List<SortKey> sortKeys = List.of();
    private Expr filter;
    private List<DataRow> index;
    private int builtVersion = -1;

    public DataView() {}

    public DataView(DataTable table) {
        this.table = table;
    }

    public DataTable Table() {
        return table;
    }

    public void Table(DataTable value) {
        this.table = value;
        this.sortKeys = value == null ? List.of() : parseSort(value, sort);
        this.filter = value == null ? null : parseFilter(value, rowFilter);
        this.index = null;
    }

    public String Sort() {
        return sort;
    }

    public void Sort(String value) {
        String v = value == null ? "" : value;
        if (table == null) {
            sort = v;
            return;
        }
        List<SortKey> keys = parseSort(table, v);
        List<DataRow> built = build(keys, filter);
        sort = v;
        sortKeys = keys;
        commit(built);
    }

    public String RowFilter() {
        return rowFilter;
    }

    public void RowFilter(String value) {
        String v = value == null ? "" : value;
        if (table == null) {
            rowFilter = v;
            return;
        }
        Expr f = parseFilter(table, v);
        List<DataRow> built = build(sortKeys, f);
        rowFilter = v;
        filter = f;
        commit(built);
    }

    private void commit(List<DataRow> built) {
        index = built;
        builtVersion = table.version();
    }

    private List<DataRow> rows() {
        if (table == null) return List.of();
        if (index == null || builtVersion != table.version()) {
            commit(build(sortKeys, filter));
        }
        return index;
    }

    public int Count() {
        return rows().size();
    }

    public DataRowView get(int recordIndex) {
        List<DataRow> r = rows();
        if (recordIndex < 0 || recordIndex >= r.size()) {
            throw new IndexOutOfRangeException("Index " + recordIndex + " is either negative or above rows count.");
        }
        return new DataRowView(this, r.get(recordIndex));
    }

    public int Find(Object key) {
        return Find(new Object[] {key});
    }

    public int Find(Object[] key) {
        if (sortKeys.isEmpty()) {
            throw new ArgumentException("Find finds a row based on a Sort order, and no Sort order is specified.");
        }
        if (key == null || key.length != sortKeys.size()) {
            throw new ArgumentException("Expecting " + sortKeys.size() + " value(s) for the key being indexed, but received "
                    + (key == null ? 0 : key.length) + " value(s).");
        }
        Object[] k = new Object[key.length];
        for (int i = 0; i < key.length; i++) {
            k[i] = DataColumn.coerce(sortKeys.get(i).column, key[i]);
        }
        List<DataRow> r = rows();
        for (int i = 0; i < r.size(); i++) {
            boolean all = true;
            for (int j = 0; j < k.length && all; j++) {
                all = compareValues(r.get(i).get(sortKeys.get(j).column), k[j]) == 0;
            }
            if (all) return i;
        }
        return -1;
    }

    @Override
    public Iterator<DataRowView> iterator() {
        List<DataRow> r = rows();
        List<DataRowView> views = new ArrayList<>(r.size());
        for (DataRow row : r) views.add(new DataRowView(this, row));
        return Collections.unmodifiableList(views).iterator();
    }

    // ------------------------------------------------------------------ building

    private List<DataRow> build(List<SortKey> keys, Expr f) {
        List<DataRow> rows = table.Rows().snapshot();
        if (f != null) {
            List<DataRow> kept = new ArrayList<>();
            for (DataRow r : rows) {
                Object v = f.eval(r);
                if (v == DBNull.Value) continue;
                if (!(v instanceof Boolean b)) {
                    throw new EvaluateException("Filter expression '" + rowFilterText(f) + "' does not evaluate to a Boolean term.");
                }
                if (b) kept.add(r);
            }
            rows = kept;
        }
        if (!keys.isEmpty()) {
            Comparator<DataRow> cmp = (a, b) -> {
                for (SortKey k : keys) {
                    int c = compareValues(a.get(k.column), b.get(k.column));
                    if (c != 0) return k.descending ? -c : c;
                }
                return 0;
            };
            rows.sort(cmp);   // stable: ties keep table order
        }
        return rows;
    }

    private static String rowFilterText(Expr f) {
        return f.text;
    }

    // ------------------------------------------------------------------ sort parsing

    private static final class SortKey {
        final DataColumn column;
        final boolean descending;

        SortKey(DataColumn column, boolean descending) {
            this.column = column;
            this.descending = descending;
        }
    }

    private static List<SortKey> parseSort(DataTable t, String sortString) {
        if (sortString == null || sortString.isEmpty()) return List.of();
        List<SortKey> keys = new ArrayList<>();
        for (String entry : sortString.split(",", -1)) {
            String cur = entry.trim();
            int len = cur.length();
            boolean desc = false;
            if (len >= 5 && cur.regionMatches(true, len - 4, " ASC", 0, 4)) {
                cur = cur.substring(0, len - 4).trim();
            } else if (len >= 6 && cur.regionMatches(true, len - 5, " DESC", 0, 5)) {
                desc = true;
                cur = cur.substring(0, len - 5).trim();
            }
            if (cur.startsWith("[")) {
                if (cur.endsWith("]") && cur.length() >= 2) {
                    cur = cur.substring(1, cur.length() - 1);
                } else {
                    throw new ArgumentException(entry + " isn't a valid Sort string entry.");
                }
            }
            DataColumn c = t.Columns().find(cur);
            if (c == null) {
                throw new IndexOutOfRangeException("Cannot find column " + cur + ".");
            }
            keys.add(new SortKey(c, desc));
        }
        return keys;
    }

    // ------------------------------------------------------------------ comparison

    private static final Collator COLLATOR;

    static {
        COLLATOR = Collator.getInstance(Locale.US);
        COLLATOR.setStrength(Collator.SECONDARY);   // case-insensitive, accent-sensitive
    }

    private static String trimEndSpaces(String s) {
        int n = s.length();
        while (n > 0 && (s.charAt(n - 1) == ' ' || s.charAt(n - 1) == '　')) n--;
        return s.substring(0, n);
    }

    private static BigDecimal decimal(Number n) {
        if (n instanceof BigDecimal bd) return bd;
        if (n instanceof Double || n instanceof Float) return new BigDecimal(Double.toString(n.doubleValue()));
        return BigDecimal.valueOf(n.longValue());
    }

    /** .NET DataTable value comparison (CaseSensitive = false, en-US). DBNull is smallest. */
    static int compareValues(Object a, Object b) {
        boolean an = a == null || a == DBNull.Value;
        boolean bn = b == null || b == DBNull.Value;
        if (an || bn) return an == bn ? 0 : (an ? -1 : 1);
        if (a instanceof String x && b instanceof String y) {
            return COLLATOR.compare(trimEndSpaces(x), trimEndSpaces(y));
        }
        if (a instanceof Number x && b instanceof Number y) {
            if ((x instanceof Double || x instanceof Float || y instanceof Double || y instanceof Float)
                    && (Double.isNaN(x.doubleValue()) || Double.isNaN(y.doubleValue())
                    || Double.isInfinite(x.doubleValue()) || Double.isInfinite(y.doubleValue()))) {
                return Double.compare(x.doubleValue(), y.doubleValue());
            }
            return decimal(x).compareTo(decimal(y));
        }
        if (a instanceof Boolean x && b instanceof Boolean y) return Boolean.compare(x, y);
        if (a instanceof LocalDateTime x && b instanceof LocalDateTime y) return x.compareTo(y);
        if (a.getClass() == b.getClass() && a instanceof Comparable<?>) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            int c = ((Comparable) a).compareTo(b);
            return c;
        }
        if (a instanceof byte[] || b instanceof byte[]) {
            throw new ArgumentException("Type 'System.Byte[]' is not comparable.");
        }
        return COLLATOR.compare(trimEndSpaces(Convert.ToString(a)), trimEndSpaces(Convert.ToString(b)));
    }

    // ------------------------------------------------------------------ RowFilter expressions

    private abstract static class Expr {
        String text = "";

        abstract Object eval(DataRow r);
    }

    private static final class Literal extends Expr {
        final Object value;

        Literal(Object value) {
            this.value = value;
        }

        @Override
        Object eval(DataRow r) {
            return value;
        }
    }

    private static final class ColumnRef extends Expr {
        final DataColumn column;

        ColumnRef(DataColumn column) {
            this.column = column;
        }

        @Override
        Object eval(DataRow r) {
            return r.get(column);
        }
    }

    private static Boolean truth(Object v, String op) {
        if (v == null || v == DBNull.Value) return null;
        if (v instanceof Boolean b) return b;
        throw new EvaluateException("Cannot perform '" + op + "' operation on " + clr(v) + ".");
    }

    private static String clr(Object v) {
        return "System." + SqlBatch.clrTypeName(v.getClass());
    }

    private static final class Not extends Expr {
        final Expr e;

        Not(Expr e) {
            this.e = e;
        }

        @Override
        Object eval(DataRow r) {
            Boolean b = truth(e.eval(r), "Not");
            return b == null ? DBNull.Value : !b;
        }
    }

    private static final class Logical extends Expr {
        final boolean and;
        final Expr l, r;

        Logical(boolean and, Expr l, Expr r) {
            this.and = and;
            this.l = l;
            this.r = r;
        }

        @Override
        Object eval(DataRow row) {
            String op = and ? "And" : "Or";
            Boolean a = truth(l.eval(row), op);
            if (and && Boolean.FALSE.equals(a)) return false;
            if (!and && Boolean.TRUE.equals(a)) return true;
            Boolean b = truth(r.eval(row), op);
            if (and && Boolean.FALSE.equals(b)) return false;
            if (!and && Boolean.TRUE.equals(b)) return true;
            if (a == null || b == null) return DBNull.Value;
            return and ? (a && b) : (a || b);
        }
    }

    /** Converts a string operand to the type of the other operand (.NET BinaryNode). */
    private static Object toTypeOf(String s, Object other, String op) {
        try {
            if (other instanceof Number) {
                return new BigDecimal(s.trim());
            }
            if (other instanceof Boolean) {
                return Convert.ToBoolean(s);
            }
            if (other instanceof LocalDateTime) {
                return DateTimes.Parse(s, CultureInfo.InvariantCulture);
            }
            if (other instanceof UUID) {
                return UUID.fromString(s.trim());
            }
            return s;
        } catch (RuntimeException e) {
            throw new EvaluateException("Cannot perform '" + op + "' operation on System.String and " + clr(other) + ".");
        }
    }

    private static int compareOperands(Object a, Object b, String op) {
        if (a instanceof String s && !(b instanceof String)) a = toTypeOf(s, b, op);
        else if (b instanceof String s && !(a instanceof String)) b = toTypeOf(s, a, op);
        boolean aNum = a instanceof Number, bNum = b instanceof Number;
        if (aNum != bNum && !(a instanceof String) && !(b instanceof String)) {
            throw new EvaluateException("Cannot perform '" + op + "' operation on " + clr(a) + " and " + clr(b) + ".");
        }
        return compareValues(a, b);
    }

    private static final class Compare extends Expr {
        final String op;
        final Expr l, r;

        Compare(String op, Expr l, Expr r) {
            this.op = op;
            this.l = l;
            this.r = r;
        }

        @Override
        Object eval(DataRow row) {
            Object a = l.eval(row), b = r.eval(row);
            if (a == null || a == DBNull.Value || b == null || b == DBNull.Value) return DBNull.Value;
            int c = compareOperands(a, b, op);
            switch (op) {
                case "=": return c == 0;
                case "<>": return c != 0;
                case "<": return c < 0;
                case ">": return c > 0;
                case "<=": return c <= 0;
                case ">=": return c >= 0;
                default: throw new UnsupportedOperationException("TODO(port) RowFilter");
            }
        }
    }

    private static final class In extends Expr {
        final Expr l;
        final List<Expr> items;

        In(Expr l, List<Expr> items) {
            this.l = l;
            this.items = items;
        }

        @Override
        Object eval(DataRow row) {
            Object a = l.eval(row);
            if (a == null || a == DBNull.Value) return DBNull.Value;
            for (Expr e : items) {
                Object b = e.eval(row);
                if (b == null || b == DBNull.Value) continue;
                if (compareOperands(a, b, "In") == 0) return true;
            }
            return false;
        }
    }

    private enum Tok { NUM, STR, ID, COL, OP, LP, RP, COMMA, EOF }

    private static final class Token {
        final Tok kind;
        final String text;
        final Object value;

        Token(Tok kind, String text, Object value) {
            this.kind = kind;
            this.text = text;
            this.value = value;
        }

        boolean kw(String k) {
            return kind == Tok.ID && text.equalsIgnoreCase(k);
        }
    }

    private static List<Token> tokenize(String s) {
        List<Token> out = new ArrayList<>();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '\'') {
                StringBuilder sb = new StringBuilder();
                int start = i++;
                boolean closed = false;
                while (i < n) {
                    char d = s.charAt(i);
                    if (d == '\'') {
                        if (i + 1 < n && s.charAt(i + 1) == '\'') {
                            sb.append('\'');
                            i += 2;
                            continue;
                        }
                        i++;
                        closed = true;
                        break;
                    }
                    sb.append(d);
                    i++;
                }
                if (!closed) {
                    throw new SyntaxErrorException("The expression contains an invalid string constant: " + s.substring(start) + ".");
                }
                out.add(new Token(Tok.STR, sb.toString(), sb.toString()));
            } else if (c == '[') {
                StringBuilder sb = new StringBuilder();
                i++;
                boolean closed = false;
                while (i < n) {
                    char d = s.charAt(i);
                    if (d == '\\' && i + 1 < n) {
                        sb.append(s.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (d == ']') {
                        i++;
                        closed = true;
                        break;
                    }
                    sb.append(d);
                    i++;
                }
                if (!closed) {
                    throw new SyntaxErrorException("Cannot find close bracket for [" + sb + ".");
                }
                out.add(new Token(Tok.COL, sb.toString(), null));
            } else if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(s.charAt(i + 1)))) {
                int start = i;
                boolean frac = false;
                while (i < n && Character.isDigit(s.charAt(i))) i++;
                if (i < n && s.charAt(i) == '.') {
                    frac = true;
                    i++;
                    while (i < n && Character.isDigit(s.charAt(i))) i++;
                }
                if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                    int save = i;
                    i++;
                    if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                    if (i < n && Character.isDigit(s.charAt(i))) {
                        frac = true;
                        while (i < n && Character.isDigit(s.charAt(i))) i++;
                    } else {
                        i = save;
                    }
                }
                String text = s.substring(start, i);
                Object v;
                if (frac) {
                    v = new BigDecimal(text);
                } else {
                    BigDecimal bd = new BigDecimal(text);
                    if (bd.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0) v = bd.intValue();
                    else if (bd.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0) v = bd.longValue();
                    else v = bd;
                }
                out.add(new Token(Tok.NUM, text, v));
            } else if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
                out.add(new Token(Tok.ID, s.substring(start, i), null));
            } else if (c == '(') {
                out.add(new Token(Tok.LP, "(", null));
                i++;
            } else if (c == ')') {
                out.add(new Token(Tok.RP, ")", null));
                i++;
            } else if (c == ',') {
                out.add(new Token(Tok.COMMA, ",", null));
                i++;
            } else if (c == '=') {
                out.add(new Token(Tok.OP, "=", null));
                i++;
            } else if (c == '<') {
                if (i + 1 < n && s.charAt(i + 1) == '>') {
                    out.add(new Token(Tok.OP, "<>", null));
                    i += 2;
                } else if (i + 1 < n && s.charAt(i + 1) == '=') {
                    out.add(new Token(Tok.OP, "<=", null));
                    i += 2;
                } else {
                    out.add(new Token(Tok.OP, "<", null));
                    i++;
                }
            } else if (c == '>') {
                if (i + 1 < n && s.charAt(i + 1) == '=') {
                    out.add(new Token(Tok.OP, ">=", null));
                    i += 2;
                } else {
                    out.add(new Token(Tok.OP, ">", null));
                    i++;
                }
            } else if (c == '-') {
                out.add(new Token(Tok.OP, "-", null));
                i++;
            } else {
                // #dates#, arithmetic, wildcards, … are not used by the source
                throw new UnsupportedOperationException("TODO(port) RowFilter");
            }
        }
        out.add(new Token(Tok.EOF, "", null));
        return out;
    }

    private static final class Parser {
        final List<Token> toks;
        final DataTable table;
        int p;

        Parser(List<Token> toks, DataTable table) {
            this.toks = toks;
            this.table = table;
        }

        Token peek() {
            return toks.get(p);
        }

        Token peek(int k) {
            return toks.get(Math.min(p + k, toks.size() - 1));
        }

        Token next() {
            return toks.get(p++);
        }

        Expr parseAll() {
            Expr e = or();
            if (peek().kind != Tok.EOF) {
                throw new SyntaxErrorException("Syntax error in the expression.");
            }
            return e;
        }

        Expr or() {
            Expr l = and();
            while (peek().kw("OR")) {
                next();
                l = new Logical(false, l, and());
            }
            return l;
        }

        Expr and() {
            Expr l = not();
            while (peek().kw("AND")) {
                next();
                l = new Logical(true, l, not());
            }
            return l;
        }

        Expr not() {
            if (peek().kw("NOT")) {
                next();
                return new Not(not());
            }
            return predicate();
        }

        Expr predicate() {
            Expr l = operand();
            Token t = peek();
            if (t.kind == Tok.OP && !t.text.equals("-")) {
                next();
                return new Compare(t.text, l, operand());
            }
            if (t.kw("NOT") && peek(1).kw("IN")) {
                next();
                next();
                return new Not(inList(l));
            }
            if (t.kw("IN")) {
                next();
                return inList(l);
            }
            if (t.kw("LIKE") || t.kw("IS") || t.kw("BETWEEN")) {
                throw new UnsupportedOperationException("TODO(port) RowFilter");
            }
            return l;
        }

        Expr inList(Expr l) {
            if (peek().kind != Tok.LP || peek(1).kind == Tok.RP) {
                throw new SyntaxErrorException("Syntax error: The items following the IN keyword must be separated by commas and be enclosed in parentheses.");
            }
            next();
            List<Expr> items = new ArrayList<>();
            while (true) {
                items.add(operand());
                Token t = next();
                if (t.kind == Tok.COMMA) continue;
                if (t.kind == Tok.RP) break;
                throw new SyntaxErrorException("Syntax error: The items following the IN keyword must be separated by commas and be enclosed in parentheses.");
            }
            return new In(l, items);
        }

        Expr operand() {
            Token t = next();
            switch (t.kind) {
                case NUM:
                    return new Literal(t.value);
                case STR:
                    return new Literal(t.value);
                case COL:
                    return column(t.text);
                case LP: {
                    Expr e = or();
                    if (next().kind != Tok.RP) {
                        throw new SyntaxErrorException("Syntax error: Missing ')'.");
                    }
                    return e;
                }
                case OP:
                    if (t.text.equals("-") && peek().kind == Tok.NUM) {
                        Object v = next().value;
                        if (v instanceof Integer i) return new Literal(-i);
                        if (v instanceof Long l) return new Literal(-l);
                        return new Literal(((BigDecimal) v).negate());
                    }
                    throw new UnsupportedOperationException("TODO(port) RowFilter");
                case ID:
                    if (t.kw("TRUE")) return new Literal(Boolean.TRUE);
                    if (t.kw("FALSE")) return new Literal(Boolean.FALSE);
                    if (t.kw("NULL")) return new Literal(DBNull.Value);
                    if (t.kw("AND") || t.kw("OR") || t.kw("IN") || t.kw("NOT") || t.kw("LIKE") || t.kw("IS")) {
                        throw new SyntaxErrorException("Syntax error: Missing operand before '" + t.text + "' operator.");
                    }
                    if (peek().kind == Tok.LP) {
                        throw new UnsupportedOperationException("TODO(port) RowFilter");   // functions
                    }
                    return column(t.text);
                default:
                    throw new SyntaxErrorException("Syntax error: Missing operand after '" + toks.get(Math.max(0, p - 2)).text + "' operator.");
            }
        }

        Expr column(String name) {
            DataColumn c = table.Columns().find(name);
            if (c == null) {
                throw new EvaluateException("Cannot find column [" + name + "].");
            }
            return new ColumnRef(c);
        }
    }

    private static Expr parseFilter(DataTable t, String text) {
        if (text == null || text.isBlank()) return null;
        Expr e = new Parser(tokenize(text), t).parseAll();
        e.text = text;
        return e;
    }
}
