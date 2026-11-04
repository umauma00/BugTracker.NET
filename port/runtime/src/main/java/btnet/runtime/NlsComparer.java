package btnet.runtime;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Emulation of Windows NLS {@code CompareString} for en-US with no flags [S2-1] — .NET Framework 4.5
 * {@code StringComparer.CurrentCulture}, {@code Comparer<string>.Default}, {@code String.Compare(a, b)}.
 *
 * <p>Both strings are canonically decomposed (NFD); category Cf characters are ignored. Levels, each consulted
 * only when every earlier level is equal over the whole string:
 * <ol>
 * <li>primary: {@code -}, {@code '} and U+00AD are skipped (word sort); every other character is weighted in
 * the groups symbols &lt; digits &lt; letters (space first among symbols, then by code point — runtime-only
 * {@code TODO(port): pin the symbol order from the Windows default sort-weight table}); digits by value;
 * letters by lower-case base letter, Latin {@code a}–{@code z} first, other scripts after them by code point;
 * a primary prefix sorts first;</li>
 * <li>secondary: the combining marks of each base character, unaccented before accented;</li>
 * <li>tertiary: at the first case difference, lower-case before upper-case (omitted by {@link #IGNORE_CASE});</li>
 * <li>word-sort tie-break: at the first difference in the skipped characters, the string without a skipped
 * character there sorts first.</li>
 * </ol>
 * {@code null} sorts before every string. {@code java.text.Collator} is banned (it ignores the space).
 */
public final class NlsComparer implements Comparator<String> {

    /** The en-US culture comparer ({@link Strings#CULTURE_COMPARER}). */
    public static final NlsComparer INSTANCE = new NlsComparer(true);

    /** The same comparer without the case level ({@code DataView} sorting; {@code DataTable.CaseSensitive = false}). */
    public static final NlsComparer IGNORE_CASE = new NlsComparer(false);

    private static final int GROUP_SYMBOL = 0;
    private static final int GROUP_DIGIT = 1 << 22;
    private static final int GROUP_LETTER = 2 << 22;

    private final boolean caseSensitive;

    private NlsComparer(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    @Override
    public int compare(String a, String b) {
        if (a == null) {
            return b == null ? 0 : -1;
        }
        if (b == null) {
            return 1;
        }
        if (a.equals(b)) {
            return 0;
        }
        Key ka = new Key(a);
        Key kb = new Key(b);

        // 1. primary
        int n = Math.min(ka.primary.size(), kb.primary.size());
        for (int i = 0; i < n; i++) {
            int c = Integer.compare(ka.primary.get(i), kb.primary.get(i));
            if (c != 0) {
                return c;
            }
        }
        if (ka.primary.size() != kb.primary.size()) {
            return ka.primary.size() < kb.primary.size() ? -1 : 1;
        }
        // 2. secondary (same element count from here on)
        for (int i = 0; i < n; i++) {
            int c = compareMarks(ka.marks.get(i), kb.marks.get(i));
            if (c != 0) {
                return c;
            }
        }
        // 3. tertiary
        if (caseSensitive) {
            for (int i = 0; i < n; i++) {
                boolean ua = ka.upper.get(i);
                boolean ub = kb.upper.get(i);
                if (ua != ub) {
                    return ua ? 1 : -1;
                }
            }
        }
        // 4. word-sort tie-break
        int m = Math.min(ka.skipped.size(), kb.skipped.size());
        for (int i = 0; i < m; i++) {
            long sa = ka.skipped.get(i);
            long sb = kb.skipped.get(i);
            int pa = (int) (sa >>> 32);
            int pb = (int) (sb >>> 32);
            if (pa != pb) {
                // the string whose skipped character comes later has none at the earlier position: it sorts first
                return pa < pb ? 1 : -1;
            }
            int c = Integer.compare((int) sa, (int) sb);
            if (c != 0) {
                return c;
            }
        }
        if (ka.skipped.size() != kb.skipped.size()) {
            return ka.skipped.size() < kb.skipped.size() ? -1 : 1;
        }
        return 0;
    }

    private static int compareMarks(int[] x, int[] y) {
        int n = Math.min(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int c = Integer.compare(x[i], y[i]);
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(x.length, y.length);
    }

    private static boolean isSkipped(int cp) {
        return cp == '-' || cp == '\'' || cp == 0x00AD;
    }

    private static boolean isMark(int cp) {
        int t = Character.getType(cp);
        return t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK
                || t == Character.ENCLOSING_MARK;
    }

    private static int primaryWeight(int cp) {
        if (Character.isLetter(cp)) {
            int lower = Character.toLowerCase(cp);
            if (lower >= 'a' && lower <= 'z') {
                return GROUP_LETTER + (lower - 'a');
            }
            return GROUP_LETTER + 26 + lower;
        }
        if (Character.getType(cp) == Character.DECIMAL_DIGIT_NUMBER) {
            return GROUP_DIGIT + Character.digit(cp, 10);
        }
        if (cp == ' ') {
            return GROUP_SYMBOL;
        }
        return GROUP_SYMBOL + 1 + cp;
    }

    /** The per-level sort key of one string. */
    private static final class Key {
        final List<Integer> primary = new ArrayList<>();
        final List<int[]> marks = new ArrayList<>();
        final List<Boolean> upper = new ArrayList<>();
        /** (element position << 32) | code point of each skipped character, in string order. */
        final List<Long> skipped = new ArrayList<>();

        Key(String s) {
            String d = Normalizer.normalize(s, Normalizer.Form.NFD);
            List<Integer> pendingMarks = null;
            int i = 0;
            while (i < d.length()) {
                int cp = d.codePointAt(i);
                i += Character.charCount(cp);
                if (Character.getType(cp) == Character.FORMAT) {
                    continue;                                   // Cf: ignored at every level
                }
                if (isSkipped(cp)) {
                    closeMarks(pendingMarks);
                    pendingMarks = null;
                    skipped.add(((long) primary.size() << 32) | (cp & 0xFFFFFFFFL));
                    continue;
                }
                if (isMark(cp) && pendingMarks != null) {
                    pendingMarks.add(cp);                       // accent of the preceding base
                    continue;
                }
                closeMarks(pendingMarks);
                // a base character (a mark without a base is weighted as a symbol of its own)
                primary.add(primaryWeight(cp));
                upper.add(Character.isUpperCase(cp) || Character.isTitleCase(cp));
                pendingMarks = new ArrayList<>();
            }
            closeMarks(pendingMarks);
        }

        private void closeMarks(List<Integer> pending) {
            if (pending == null) {
                return;
            }
            int[] a = new int[pending.size()];
            for (int k = 0; k < a.length; k++) {
                a[k] = pending.get(k);
            }
            marks.add(a);
        }
    }
}
