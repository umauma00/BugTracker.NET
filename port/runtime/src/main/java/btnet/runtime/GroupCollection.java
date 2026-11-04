package btnet.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Stands for .NET {@code GroupCollection} [S2-6]: {@code get(int n)}, {@code get(String name)}, {@code Count()},
 * iterable in group-number order. Group 0 is the match; numbering is .NET's (unnamed groups by position of
 * their opening parenthesis, then named groups in order of first appearance; a name that is a number is that
 * number). An unknown index or name → an unsuccessful {@link Group}, never {@code null}.
 */
public final class GroupCollection implements Iterable<Group> {
    private final List<Group> groups = new ArrayList<>();
    private final Map<String, Integer> names;

    GroupCollection(Map<String, Integer> names) {
        this.names = names == null ? Map.of() : names;
    }

    void add(Group g) {
        groups.add(g);
    }

    void set(int i, Group g) {
        groups.set(i, g);
    }

    /** .NET {@code Groups[n]}. */
    public Group get(int n) {
        if (n < 0 || n >= groups.size()) {
            return Group.unsuccessful();
        }
        return groups.get(n);
    }

    /** .NET {@code Groups["name"]}. */
    public Group get(String name) {
        if (name == null) {
            throw new ArgumentNullException("groupname");
        }
        Integer i = names.get(name);
        if (i != null) {
            return get(i.intValue());
        }
        if (!name.isEmpty() && name.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
            try {
                return get(Integer.parseInt(name));
            } catch (NumberFormatException e) {
                return Group.unsuccessful();
            }
        }
        return Group.unsuccessful();
    }

    /** .NET {@code Count}. */
    public int Count() {
        return groups.size();
    }

    @Override
    public Iterator<Group> iterator() {
        return Collections.unmodifiableList(groups).iterator();
    }
}
