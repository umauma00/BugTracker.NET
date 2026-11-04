package btnet.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Stands for .NET {@code System.Text.RegularExpressions.Regex} (RULEBOOK §2A Regex, §3 C21/C26): compiled with
 * {@code UNIX_LINES | UNICODE_CHARACTER_CLASS} (+ {@code CASE_INSENSITIVE|UNICODE_CASE} for IgnoreCase,
 * {@code MULTILINE}, {@code DOTALL} for Singleline, {@code COMMENTS} for IgnorePatternWhitespace after escaping
 * {@code #} and white space inside character classes). {@code Compiled}/{@code CultureInvariant} are no-ops.
 * Replacement strings use .NET syntax ({@code $1 ${name} $$ $& $` $' $+ $_}; {@code \} literal) and are expanded
 * here; MatchEvaluator results are inserted literally. {@code Split} keeps empties and captured groups.
 */
public final class DotNetRegex {
    private final Pattern pattern;
    private final String source;
    private final int options;
    /** .NET group number → Java group index (S2-6: unnamed groups first, then named ones). */
    private final int[] netToJava;
    /** Group name → .NET group number. */
    private final Map<String, Integer> netNames;

    public DotNetRegex(String pattern) {
        this(pattern, RegexOptions.None);
    }

    public DotNetRegex(String pattern, int options) {
        if (pattern == null) {
            throw new ArgumentNullException("pattern");
        }
        if ((options & (RegexOptions.RightToLeft | RegexOptions.ECMAScript | RegexOptions.ExplicitCapture)) != 0) {
            throw new UnsupportedOperationException("TODO(port): RegexOptions RightToLeft/ECMAScript/ExplicitCapture");
        }
        this.source = pattern;
        this.options = options;
        int flags = Pattern.UNIX_LINES | Pattern.UNICODE_CHARACTER_CLASS;
        String p = pattern;
        if ((options & RegexOptions.IgnoreCase) != 0) {
            flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        }
        if ((options & RegexOptions.Multiline) != 0) {
            flags |= Pattern.MULTILINE;
        }
        if ((options & RegexOptions.Singleline) != 0) {
            flags |= Pattern.DOTALL;
        }
        if ((options & RegexOptions.IgnorePatternWhitespace) != 0) {
            p = escapeClassWhitespace(p);
            flags |= Pattern.COMMENTS;
        }
        try {
            this.pattern = Pattern.compile(p, flags);
        } catch (PatternSyntaxException e) {
            throw new ArgumentException("parsing \"" + pattern + "\" - " + e.getDescription());
        }
        Map<String, Integer> javaNames = this.pattern.namedGroups();
        java.util.TreeMap<Integer, String> namedByJava = new java.util.TreeMap<>();
        for (Map.Entry<String, Integer> e : javaNames.entrySet()) {
            namedByJava.put(e.getValue(), e.getKey());
        }
        int count = this.pattern.matcher("").groupCount();
        int[] map = new int[count + 1];
        int k = 1;
        for (int j = 1; j <= count; j++) {
            if (!namedByJava.containsKey(j)) {
                map[k++] = j;
            }
        }
        Map<String, Integer> names = new java.util.HashMap<>();
        for (Map.Entry<Integer, String> e : namedByJava.entrySet()) {
            names.put(e.getValue(), k);
            map[k++] = e.getKey();
        }
        this.netToJava = map;
        this.netNames = java.util.Collections.unmodifiableMap(names);
    }

    /** The value of .NET group {@code g} in {@code m}, or {@code null} if it did not participate. */
    private String group(Matcher m, int g) {
        return m.group(netToJava[g]);
    }

    /** Inside {@code [...]}, rewrite {@code #} and white space as {@code \xHH} (literal in .NET there). */
    static String escapeClassWhitespace(String p) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c == '\\' && i + 1 < p.length()) {
                sb.append(c).append(p.charAt(++i));
                continue;
            }
            if (depth == 0) {
                if (c == '[') {
                    depth = 1;
                    sb.append(c);
                    if (i + 1 < p.length() && p.charAt(i + 1) == '^') {
                        sb.append('^');
                        i++;
                    }
                    if (i + 1 < p.length() && p.charAt(i + 1) == ']') {
                        sb.append("\\]");
                        i++;
                    }
                } else {
                    sb.append(c);
                }
                continue;
            }
            if (c == ']') {
                depth = 0;
                sb.append(c);
            } else if (c == '#' || Character.isWhitespace(c) || c == ' ') {
                sb.append(String.format(java.util.Locale.ROOT, "\\x{%X}", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return source;
    }

    /** .NET {@code Options}. */
    public int Options() {
        return options;
    }

    private static void checkInput(String input) {
        if (input == null) {
            throw new ArgumentNullException("input");
        }
    }

    // ------------------------------------------------------------------ matching

    private Match toMatch(Matcher m, String input) {
        GroupCollection groups = new GroupCollection(netNames);
        int next = m.end() == m.start() ? m.end() + 1 : m.end();
        Match whole = new Match(true, m.group(), m.start(), groups, this, input, next);
        groups.add(whole);
        for (int g = 1; g < netToJava.length; g++) {
            String v = group(m, g);
            groups.add(v == null ? Group.unsuccessful() : new Group(true, v, m.start(netToJava[g])));
        }
        return whole;
    }

    Match matchAt(String input, int start) {
        if (start > input.length()) {
            return Match.failed();
        }
        Matcher m = pattern.matcher(input);
        if (m.find(start)) {
            return toMatch(m, input);
        }
        return Match.failed();
    }

    public boolean IsMatch(String input) {
        checkInput(input);
        return pattern.matcher(input).find();
    }

    public Match Match(String input) {
        checkInput(input);
        return matchAt(input, 0);
    }

    /** .NET {@code Matches} [C26] → {@code List<Match>}. */
    public List<Match> Matches(String input) {
        checkInput(input);
        List<Match> out = new ArrayList<>();
        Matcher m = pattern.matcher(input);
        while (m.find()) {
            out.add(toMatch(m, input));
        }
        return out;
    }

    public static boolean IsMatch(String input, String pattern) {
        return new DotNetRegex(pattern).IsMatch(input);
    }

    public static boolean IsMatch(String input, String pattern, int options) {
        return new DotNetRegex(pattern, options).IsMatch(input);
    }

    public static Match Match(String input, String pattern) {
        return new DotNetRegex(pattern).Match(input);
    }

    public static Match Match(String input, String pattern, int options) {
        return new DotNetRegex(pattern, options).Match(input);
    }

    public static List<Match> Matches(String input, String pattern) {
        return new DotNetRegex(pattern).Matches(input);
    }

    public static List<Match> Matches(String input, String pattern, int options) {
        return new DotNetRegex(pattern, options).Matches(input);
    }

    // ------------------------------------------------------------------ Replace

    public String Replace(String input, String replacement) {
        checkInput(input);
        if (replacement == null) {
            throw new ArgumentNullException("replacement");
        }
        StringBuilder sb = new StringBuilder();
        Matcher m = pattern.matcher(input);
        int last = 0;
        while (m.find()) {
            sb.append(input, last, m.start());
            expand(sb, replacement, m, input);
            last = m.end();
        }
        sb.append(input, last, input.length());
        return sb.toString();
    }

    public String Replace(String input, MatchEvaluator evaluator) {
        checkInput(input);
        if (evaluator == null) {
            throw new ArgumentNullException("evaluator");
        }
        StringBuilder sb = new StringBuilder();
        Matcher m = pattern.matcher(input);
        int last = 0;
        while (m.find()) {
            sb.append(input, last, m.start());
            String r = evaluator.Invoke(toMatch(m, input));
            if (r != null) {
                sb.append(r);
            }
            last = m.end();
        }
        sb.append(input, last, input.length());
        return sb.toString();
    }

    public static String Replace(String input, String pattern, String replacement) {
        return new DotNetRegex(pattern).Replace(input, replacement);
    }

    public static String Replace(String input, String pattern, String replacement, int options) {
        return new DotNetRegex(pattern, options).Replace(input, replacement);
    }

    public static String Replace(String input, String pattern, MatchEvaluator evaluator) {
        return new DotNetRegex(pattern).Replace(input, evaluator);
    }

    public static String Replace(String input, String pattern, MatchEvaluator evaluator, int options) {
        return new DotNetRegex(pattern, options).Replace(input, evaluator);
    }

    /** .NET replacement-pattern expansion ({@code RegexParser.ScanReplacement}). */
    private void expand(StringBuilder sb, String r, Matcher m, String input) {
        int n = r.length();
        for (int i = 0; i < n; i++) {
            char c = r.charAt(i);
            if (c != '$' || i + 1 >= n) {
                sb.append(c);
                continue;
            }
            char d = r.charAt(i + 1);
            if (d == '$') {
                sb.append('$');
                i++;
            } else if (d == '&') {
                sb.append(m.group());
                i++;
            } else if (d == '`') {
                sb.append(input, 0, m.start());
                i++;
            } else if (d == '\'') {
                sb.append(input, m.end(), input.length());
                i++;
            } else if (d == '_') {
                sb.append(input);
                i++;
            } else if (d == '+') {
                int g = netToJava.length - 1;
                if (g > 0 && group(m, g) != null) {
                    sb.append(group(m, g));
                }
                i++;
            } else if (d >= '0' && d <= '9') {
                int j = i + 1;
                while (j < n && r.charAt(j) >= '0' && r.charAt(j) <= '9') {
                    j++;
                }
                int g;
                try {
                    g = Integer.parseInt(r.substring(i + 1, j));
                } catch (NumberFormatException e) {
                    g = -1;
                }
                if (g >= 0 && g < netToJava.length) {
                    String v = group(m, g);
                    if (v != null) {
                        sb.append(v);
                    }
                    i = j - 1;
                } else {
                    sb.append('$');
                }
            } else if (d == '{') {
                int close = r.indexOf('}', i + 2);
                if (close < 0) {
                    sb.append('$');
                    continue;
                }
                String name = r.substring(i + 2, close);
                Integer g = null;
                if (!name.isEmpty() && name.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
                    try {
                        int k = Integer.parseInt(name);
                        if (k < netToJava.length) {
                            g = k;
                        }
                    } catch (NumberFormatException e) {
                        g = null;
                    }
                } else {
                    g = netNames.get(name);
                }
                if (g == null) {
                    sb.append('$');
                    continue;
                }
                String v = group(m, g);
                if (v != null) {
                    sb.append(v);
                }
                i = close;
            } else {
                sb.append('$');
            }
        }
    }

    // ------------------------------------------------------------------ Split

    /** .NET {@code Regex.Split}: no match → {input}; keeps leading/trailing empties and captured groups. */
    public String[] Split(String input) {
        checkInput(input);
        List<String> out = new ArrayList<>();
        Matcher m = pattern.matcher(input);
        int prev = 0;
        boolean any = false;
        while (m.find()) {
            any = true;
            out.add(input.substring(prev, m.start()));
            prev = m.end();
            for (int g = 1; g < netToJava.length; g++) {
                if (group(m, g) != null) {
                    out.add(group(m, g));        // .NET order: by group number
                }
            }
        }
        if (!any) {
            return new String[] {input};
        }
        out.add(input.substring(prev));
        return out.toArray(new String[0]);
    }

    public static String[] Split(String input, String pattern) {
        return new DotNetRegex(pattern).Split(input);
    }

    public static String[] Split(String input, String pattern, int options) {
        return new DotNetRegex(pattern, options).Split(input);
    }

    /** .NET {@code Regex.Escape}. */
    public static String Escape(String str) {
        if (str == null) {
            throw new ArgumentNullException("str");
        }
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            switch (c) {
                case '\\': case '*': case '+': case '?': case '|': case '{': case '[': case '(': case ')':
                case '^': case '$': case '.': case '#': case ' ':
                    sb.append('\\').append(c);
                    break;
                case '\t': sb.append("\\t"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\f': sb.append("\\f"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}
