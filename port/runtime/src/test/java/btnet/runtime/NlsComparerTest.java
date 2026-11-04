package btnet.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** [S2-1] acceptance vectors (verify on the original when D-001 lifts). */
class NlsComparerTest {
    private static final NlsComparer C = NlsComparer.INSTANCE;

    private static void less(String a, String b) {
        assertTrue(C.compare(a, b) < 0, "expected \"" + a + "\" < \"" + b + "\"");
        assertTrue(C.compare(b, a) > 0, "expected \"" + b + "\" > \"" + a + "\"");
    }

    @Test
    void cultureComparerIsNls() {
        assertSame(NlsComparer.INSTANCE, Strings.CULTURE_COMPARER);
    }

    @Test
    void spaceIsNotIgnorable() {
        less("in progress", "inactive");
        less("inactive", "info");
        less("New York", "Newark");
        less("New Jersey", "New York");
        less("Newark", "Newton");
    }

    @Test
    void sortsStatusListLikeDotNet() {
        List<String> l = new ArrayList<>(Arrays.asList("info", "inactive", "in progress"));
        l.sort(Strings.CULTURE_COMPARER);
        assertEquals(List.of("in progress", "inactive", "info"), l);
    }

    @Test
    void wordSortSkipsHyphenAndApostrophe() {
        less("ab", "a-z");    // primary "az" > "ab"
        less("ab", "a-b");    // tie-break: without the hyphen first
        less("cant", "can't");
        assertEquals(0, C.compare("a-b", "a-b"));
    }

    @Test
    void caseAccentAndDigits() {
        less("apple", "Apple");
        less("A", "b");
        less("10", "9");
        less("resume", "résumé");
        less("9", "a");       // digits before letters
        less("!", "0");       // symbols before digits
        assertEquals(0, C.compare("é", "é"));
        assertEquals(0, C.compare("a​b", "ab")); // Cf ignored
    }

    @Test
    void nullsFirst() {
        assertEquals(0, C.compare(null, null));
        less(null, "");
        less("", "a");
    }

    @Test
    void treeMapMergesCanonicallyEquivalentKeys() {
        TreeMap<String, Integer> m = new TreeMap<>(Strings.CULTURE_COMPARER);
        m.put("é", 1);
        m.put("é", 2);
        assertEquals(1, m.size());
        assertEquals(2, m.get("é"));
    }

    @Test
    void ignoreCaseVariantDropsOnlyTheCaseLevel() {
        assertEquals(0, NlsComparer.IGNORE_CASE.compare("Alpha", "alpha"));
        assertTrue(NlsComparer.IGNORE_CASE.compare("resume", "résumé") < 0);
        assertTrue(NlsComparer.IGNORE_CASE.compare("in progress", "inactive") < 0);
    }
}
