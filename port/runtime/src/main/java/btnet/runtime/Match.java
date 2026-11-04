package btnet.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Stands for .NET {@code System.Text.RegularExpressions.Match} and, for its {@link #Groups()} entries, {@code Group}
 * (a .NET Match is its own group 0). Property convention: {@code m.Value()}, {@code m.Success()},
 * {@code m.Index()}, {@code m.Length()}, {@code m.Groups().get(1).Value()}; {@code m.ToString()} → {@code m.Value()}.
 */
public class Match {

    /** .NET {@code GroupCollection}: integer and name indexers; an unknown group is an unmatched empty group. */
    public static final class GroupCollection extends ArrayList<Match> {
        private static final long serialVersionUID = 1L;
        private final transient java.util.Map<String, Integer> names;

        GroupCollection(java.util.Map<String, Integer> names) {
            this.names = names;
        }

        @Override
        public Match get(int index) {
            if (index < 0 || index >= size()) {
                return Match.failed(); // .NET returns Group.Empty-like unmatched group
            }
            return super.get(index);
        }

        /** .NET {@code Groups["name"]}. */
        public Match get(String name) {
            Integer i = names == null ? null : names.get(name);
            if (i == null) {
                try {
                    return get(Integer.parseInt(name));
                } catch (NumberFormatException e) {
                    return Match.failed();
                }
            }
            return get(i.intValue());
        }

        /** .NET {@code Count}. */
        public int Count() {
            return size();
        }
    }

    private final boolean success;
    private final String value;
    private final int index;
    private final GroupCollection groups;
    private final DotNetRegex regex;
    private final String input;
    private final int nextStart;

    Match(boolean success, String value, int index, GroupCollection groups, DotNetRegex regex, String input,
            int nextStart) {
        this.success = success;
        this.value = value;
        this.index = index;
        this.groups = groups;
        this.regex = regex;
        this.input = input;
        this.nextStart = nextStart;
    }

    static Match failed() {
        return new Match(false, "", 0, new GroupCollection(null), null, null, 0);
    }

    /** .NET {@code Match.Empty}. */
    public static final Match Empty = failed();

    public boolean Success() {
        return success;
    }

    public String Value() {
        return value;
    }

    public int Index() {
        return index;
    }

    public int Length() {
        return value.length();
    }

    /** .NET {@code Groups}; entry 0 is the whole match. */
    public GroupCollection Groups() {
        return groups;
    }

    /** .NET {@code NextMatch()}. */
    public Match NextMatch() {
        if (!success || regex == null) {
            return failed();
        }
        return regex.matchAt(input, nextStart);
    }

    @Override
    public String toString() {
        return value;
    }

    static List<Match> list() {
        return new ArrayList<>();
    }
}
