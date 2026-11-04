package btnet.runtime.data;

import btnet.runtime.ArgumentException;
import btnet.runtime.IndexOutOfRangeException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * .NET {@code System.Data.DataRowCollection} ({@code dt.Rows()}): {@code Count()},
 * {@code get(i)} ({@code IndexOutOfRangeException("There is no row at position i.")}),
 * {@code Add}, {@code stream()} (for {@code AsEnumerable()}), iterable in row order.
 */
public class DataRowCollection implements Iterable<DataRow> {
    private final DataTable table;
    private final List<DataRow> rows = new ArrayList<>();

    DataRowCollection(DataTable table) {
        this.table = table;
    }

    public int Count() {
        return rows.size();
    }

    public DataRow get(int index) {
        if (index < 0 || index >= rows.size()) {
            throw new IndexOutOfRangeException("There is no row at position " + index + ".");
        }
        return rows.get(index);
    }

    public void Add(DataRow row) {
        if (row.Table() != table) {
            throw new ArgumentException("This row already belongs to another table.");
        }
        if (row.attached) {
            throw new ArgumentException("This row already belongs to this table.");
        }
        row.attached = true;
        rows.add(row);
        table.changed();
    }

    public DataRow Add(Object... values) {
        DataRow r = table.NewRow();
        if (values != null) {
            r.ItemArray(values);
        }
        Add(r);
        return r;
    }

    public void InsertAt(DataRow row, int pos) {
        if (row.Table() != table) {
            throw new ArgumentException("This row already belongs to another table.");
        }
        if (row.attached) {
            throw new ArgumentException("This row already belongs to this table.");
        }
        row.attached = true;
        rows.add(Math.max(0, Math.min(pos, rows.size())), row);
        table.changed();
    }

    public int IndexOf(DataRow row) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i) == row) return i;
        }
        return -1;
    }

    public boolean Contains(DataRow row) {
        return IndexOf(row) >= 0;
    }

    public void Remove(DataRow row) {
        int i = IndexOf(row);
        if (i < 0) {
            throw new IndexOutOfRangeException("The given DataRow is not in the current DataRowCollection.");
        }
        RemoveAt(i);
    }

    public void RemoveAt(int index) {
        DataRow r = get(index);
        rows.remove(index);
        r.attached = false;
        table.changed();
    }

    public void Clear() {
        for (DataRow r : rows) r.attached = false;
        rows.clear();
        table.changed();
    }

    /** {@code dt.AsEnumerable()} (RULEBOOK §2A). */
    public Stream<DataRow> stream() {
        return new ArrayList<>(rows).stream();
    }

    List<DataRow> snapshot() {
        return new ArrayList<>(rows);
    }

    @Override
    public Iterator<DataRow> iterator() {
        return Collections.unmodifiableList(rows).iterator();
    }
}
