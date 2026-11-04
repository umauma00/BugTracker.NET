package btnet.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Stands for .NET {@code System.Text.RegularExpressions.Match}; {@code Match extends Group} (a .NET Match is its
 * own group 0) [S2-6]. Property convention: {@code m.Value()}, {@code m.Success()}, {@code m.Index()},
 * {@code m.Length()}, {@code m.Groups().get(1).Value()}; {@code m.ToString()} → {@code m.Value()}.
 */
public class Match extends Group {
    private final GroupCollection groups;
    private final DotNetRegex regex;
    private final String input;
    private final int nextStart;

    Match(boolean success, String value, int index, GroupCollection groups, DotNetRegex regex, String input,
            int nextStart) {
        super(success, value, index);
        this.groups = groups;
        this.regex = regex;
        this.input = input;
        this.nextStart = nextStart;
    }

    static Match failed() {
        GroupCollection g = new GroupCollection(null);
        Match m = new Match(false, "", 0, g, null, null, 0);
        g.add(m);
        return m;
    }

    /** .NET {@code Match.Empty}. */
    public static final Match Empty = failed();

    /** .NET {@code Groups}; entry 0 is the whole match. */
    public GroupCollection Groups() {
        return groups;
    }

    /** .NET {@code NextMatch()}. */
    public Match NextMatch() {
        if (!Success() || regex == null) {
            return failed();
        }
        return regex.matchAt(input, nextStart);
    }

    static List<Match> list() {
        return new ArrayList<>();
    }
}
