package btnet.runtime.data;

import btnet.runtime.ArgumentException;
import btnet.runtime.IndexOutOfRangeException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * .NET {@code System.Data.SqlClient.SqlParameterCollection} ({@code cmd.Parameters()}).
 * Iterable in insertion order ({@code foreach (SqlParameter p in cmd.Parameters)}).
 * Adding a parameter that already belongs to another collection throws
 * {@code ArgumentException("The SqlParameter is already contained by another SqlParameterCollection.")},
 * as SqlClient does (the parameter keeps its owner after the command is disposed).
 */
public class SqlParameterCollection implements Iterable<SqlParameter> {
    private final List<SqlParameter> items = new ArrayList<>();

    public SqlParameter Add(SqlParameter value) {
        if (value == null) {
            throw new ArgumentException("The SqlParameterCollection only accepts non-null SqlParameter type objects.");
        }
        if (value.parent == this) {
            throw new ArgumentException("The SqlParameter is already contained by another SqlParameterCollection.");
        }
        if (value.parent != null) {
            throw new ArgumentException("The SqlParameter is already contained by another SqlParameterCollection.");
        }
        value.parent = this;
        items.add(value);
        return value;
    }

    public SqlParameter Add(String parameterName, SqlDbType sqlDbType) {
        return Add(new SqlParameter(parameterName, sqlDbType));
    }

    public SqlParameter Add(String parameterName, SqlDbType sqlDbType, int size) {
        return Add(new SqlParameter(parameterName, sqlDbType, size));
    }

    public SqlParameter AddWithValue(String parameterName, Object value) {
        return Add(new SqlParameter(parameterName, value));
    }

    public void AddRange(SqlParameter[] values) {
        for (SqlParameter p : values) {
            Add(p);
        }
    }

    /** Convenience overload: {@code AddRange(list)} without {@code toArray}. */
    public void AddRange(Collection<? extends SqlParameter> values) {
        for (SqlParameter p : values) {
            Add(p);
        }
    }

    public int Count() {
        return items.size();
    }

    public SqlParameter get(int index) {
        if (index < 0 || index >= items.size()) {
            throw new IndexOutOfRangeException("Invalid index " + index
                    + " for this SqlParameterCollection with Count=" + items.size() + ".");
        }
        return items.get(index);
    }

    public SqlParameter get(String parameterName) {
        int i = IndexOf(parameterName);
        if (i < 0) {
            throw new IndexOutOfRangeException("An SqlParameter with ParameterName '" + parameterName
                    + "' is not contained by this SqlParameterCollection.");
        }
        return items.get(i);
    }

    public int IndexOf(String parameterName) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).ParameterName().equals(parameterName)) return i;
        }
        String wanted = parameterName == null ? null : parameterName.toUpperCase(Locale.ROOT);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).ParameterName().toUpperCase(Locale.ROOT).equals(wanted)) return i;
        }
        return -1;
    }

    public boolean Contains(String parameterName) {
        return IndexOf(parameterName) >= 0;
    }

    public void Remove(SqlParameter value) {
        if (items.remove(value)) {
            value.parent = null;
        }
    }

    public void RemoveAt(int index) {
        SqlParameter p = get(index);
        items.remove(index);
        p.parent = null;
    }

    public void Clear() {
        for (SqlParameter p : items) {
            p.parent = null;
        }
        items.clear();
    }

    /** Snapshot of the parameters in order (used by {@link SqlBatch}). */
    List<SqlParameter> list() {
        return new ArrayList<>(items);
    }

    @Override
    public Iterator<SqlParameter> iterator() {
        return java.util.Collections.unmodifiableList(items).iterator();
    }
}
