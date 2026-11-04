package btnet.runtime.web.ui;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code System.Web.UI.WebControls.DataGridColumnCollection} [S2-21]: {@code Add(column)}, {@code Count()},
 * {@code get(i)}; column order = {@code Add} order.
 */
public final class DataGridColumnCollection extends AbstractList<DataGridColumn<?>> {
    private final List<DataGridColumn<?>> columns = new ArrayList<>();

    /** .NET {@code Columns.Add(column)}; {@code null} → {@code ArgumentNullException}. */
    public void Add(DataGridColumn<?> column) {
        add(column);
    }

    /** .NET {@code Count}. */
    public int Count() {
        return columns.size();
    }

    /** .NET {@code Clear()}. */
    public void Clear() {
        columns.clear();
    }

    @Override
    public DataGridColumn<?> get(int index) {
        return columns.get(index);
    }

    @Override
    public int size() {
        return columns.size();
    }

    @Override
    public void add(int index, DataGridColumn<?> column) {
        if (column == null) {
            throw new btnet.runtime.ArgumentNullException("column");
        }
        columns.add(index, column);
    }

    @Override
    public DataGridColumn<?> set(int index, DataGridColumn<?> column) {
        if (column == null) {
            throw new btnet.runtime.ArgumentNullException("column");
        }
        return columns.set(index, column);
    }

    @Override
    public DataGridColumn<?> remove(int index) {
        return columns.remove(index);
    }
}
