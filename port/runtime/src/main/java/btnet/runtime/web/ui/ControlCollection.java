package btnet.runtime.web.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code System.Web.UI.ControlCollection}: the ordered child controls of a control (or of a page,
 * when the page base class keeps its controls in one; {@code owner} may then be {@code null}).
 * {@code foreach (Control c in x.Controls)} → {@code for (Control<?> c : x.Controls())};
 * {@code x.Controls.Count} → {@code Count()}; {@code x.Controls[i]} → {@code get(i)}.
 * Adding sets the child's {@code Parent}; an ID-less child of a naming container gets the next
 * automatic ID {@code ctlNN} of that container (ASP.NET {@code GenerateAutomaticID}).
 */
public final class ControlCollection implements Iterable<Control<?>> {
    private final Control<?> owner;
    private final ArrayList<Control<?>> list = new ArrayList<>();

    public ControlCollection(Control<?> owner) {
        this.owner = owner;
    }

    public ControlCollection() {
        this(null);
    }

    public Control<?> Owner() { return owner; }

    public void Add(Control<?> child) {
        AddAt(list.size(), child);
    }

    public void AddAt(int index, Control<?> child) {
        if (child == null) throw new btnet.runtime.ArgumentNullException("child");
        if (index < 0 || index > list.size()) {
            throw new btnet.runtime.ArgumentOutOfRangeException("index",
                    "Insertion index was out of range. Must be non-negative and less than or equal to size.");
        }
        if (child.Parent() != null) child.Parent().Controls().Remove(child);
        list.add(index, child);
        if (owner != null) {
            child.setParent(owner);
            if (child.ID() == null && child.wantsAutomaticId()) {
                Control<?> nc = owner.IsNamingContainer() ? owner : owner.NamingContainer();
                if (nc != null) child.assignAutomaticId(nc.nextAutomaticId());
            }
        }
    }

    public void Remove(Control<?> child) {
        if (list.remove(child) && owner != null) child.setParent(null);
    }

    public void RemoveAt(int index) {
        Control<?> c = get(index);
        Remove(c);
    }

    public void Clear() {
        for (Control<?> c : list) if (owner != null) c.setParent(null);
        list.clear();
    }

    public int Count() { return list.size(); }

    public Control<?> get(int index) {
        if (index < 0 || index >= list.size()) {
            throw new btnet.runtime.ArgumentOutOfRangeException("index",
                    "Index was out of range. Must be non-negative and less than the size of the collection.");
        }
        return list.get(index);
    }

    public boolean Contains(Control<?> c) { return list.contains(c); }

    public int IndexOf(Control<?> c) { return list.indexOf(c); }

    public Stream<Control<?>> stream() { return list.stream(); }

    /** Snapshot view (unmodifiable). */
    public List<Control<?>> asList() { return Collections.unmodifiableList(list); }

    @Override
    public Iterator<Control<?>> iterator() {
        return Collections.unmodifiableList(list).iterator();
    }
}
