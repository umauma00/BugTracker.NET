package btnet.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** .NET Regex semantics (docs: Substitutions in Regular Expressions; Regex.Split). */
class DotNetRegexTest {

    @Test
    void replacementSyntaxIsDotNet() {
        assertEquals("a<1>b<22>", DotNetRegex.Replace("a1b22", "[0-9]+", "<$0>"));
        assertEquals("a$b$", DotNetRegex.Replace("a1b2", "[0-9]", "$$"));
        assertEquals("x-y", DotNetRegex.Replace("y-x", "(?<a>\\w)-(?<b>\\w)", "${b}-${a}"));
        assertEquals("a\\1", DotNetRegex.Replace("a1", "([0-9])", "\\$1")); // backslash is literal in .NET
        assertEquals("$9", DotNetRegex.Replace("x", "x", "$9")); // non-existent group stays literal
        assertEquals("<scrSAFEipt>", DotNetRegex.Replace("<SCRIPT>", "<script", "<scrSAFEipt", RegexOptions.IgnoreCase));
    }

    @Test
    void evaluatorResultIsLiteral() {
        DotNetRegex re = new DotNetRegex("[a-z]+@[a-z]+");
        assertEquals("mail <$1@x>", re.Replace("mail a@x", m -> "<$1@" + m.Value().substring(2) + ">"));
    }

    @Test
    void splitKeepsEmptiesAndCaptures() {
        assertArrayEquals(new String[] {"", "a", ""}, new DotNetRegex(",").Split(",a,"));
        assertArrayEquals(new String[] {"a", "b", "", "c"}, new DotNetRegex(",").Split("a,b,,c"));
        assertArrayEquals(new String[] {"a", "1", "b"}, DotNetRegex.Split("a1b", "(1)"));
        assertArrayEquals(new String[] {"abc"}, new DotNetRegex("\\|").Split("abc"));
    }

    @Test
    void matchesAndGroups() {
        List<Match> ms = new DotNetRegex("bugid#([0-9]+)").Matches("see bugid#12 and bugid#34");
        assertEquals(2, ms.size());
        assertEquals("12", ms.get(0).Groups().get(1).Value());
        assertEquals("34", ms.get(1).Groups().get(1).toString());
        Match m = new DotNetRegex("(^[0-9,]+ )").Match("12,13 fix");
        assertTrue(m.Success());
        assertEquals("12,13 ", m.Groups().get(1).Value());
        assertFalse(m.Groups().get(7).Success());
        assertFalse(new DotNetRegex("z").Match("abc").Success());
    }

    @Test
    void ignorePatternWhitespaceKeepsHashInsideClass() {
        // print_bug.cs:28 — '#' inside [...] is literal in .NET even with IgnorePatternWhitespace
        DotNetRegex re = new DotNetRegex("https?://[-A-Za-z0-9+&@#/%?=~_()|!:,.;]*[-A-Za-z0-9+&@#/%=~_()|]",
                RegexOptions.IgnoreCase | RegexOptions.CultureInvariant | RegexOptions.IgnorePatternWhitespace
                        | RegexOptions.Compiled);
        assertEquals("http://x/a#b", re.Match("go http://x/a#b now").Value());
        assertTrue(new DotNetRegex("a b", RegexOptions.IgnorePatternWhitespace).IsMatch("ab"));
    }

    // [S2-6] .NET numbering: unnamed groups first, then named groups; unknown/non-participating → unsuccessful ""
    @Test
    void groupNumberingIsDotNet() {
        Match m = new DotNetRegex("(?<n>bug)#(\\d+)(x)?").Match("see bug#12");
        assertTrue(m.Success());
        GroupCollection g = m.Groups();
        assertEquals(4, g.Count());
        assertEquals("bug#12", g.get(0).Value());
        assertEquals("12", g.get(1).Value());
        assertEquals(8, g.get(1).Index());
        assertFalse(g.get(2).Success());
        assertEquals("", g.get(2).Value());
        assertEquals("bug", g.get(3).Value());
        assertEquals("bug", g.get("n").Value());
        assertEquals("12", g.get("1").Value());
        assertFalse(g.get("nope").Success());
        assertEquals("", g.get(99).Value());
        assertEquals(0, g.get(99).Length());
        Group whole = m;                                   // Match extends Group
        assertEquals("bug#12", whole.toString());
        assertEquals("12/bug", DotNetRegex.Replace("bug#12", "(?<n>bug)#(\\d+)", "$1/$2"));
        assertEquals("bug", DotNetRegex.Replace("bug#12", "(?<n>bug)#(\\d+)", "${n}"));
        assertArrayEquals(new String[] {"a", "-", "x", "b"}, DotNetRegex.Split("ax-b", "(?<n>x)(-)")); // group 1 = (-) first
    }
}
