package btnet.runtime.data;

import btnet.runtime.ArgumentException;
import btnet.runtime.IndexOutOfRangeException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * .NET {@code System.Data.DataTableCollection} ({@code ds.Tables()}): {@code get(i)}
 * ({@code IndexOutOfRangeException("Cannot find table i.")}), {@code get(name)}
 * (case-insensitive, {@code null} if absent), {@code Count()}, and {@code Add()} which creates a
 * table named {@code Table}, {@code Table1}, … (first free name, RULEBOOK §3 [S1-19]).
 */
public class DataTableCollection implements Iterable<DataTable> {
    private final DataSet dataSet;
    private final List<DataTable> tables = new ArrayList<>();

    DataTableCollection(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    public int Count() {
        return tables.size();
    }

    public DataTable get(int index) {
        if (index < 0 || index >= tables.size()) {
            throw new IndexOutOfRangeException("Cannot find table " + index + ".");
        }
        return tables.get(index);
    }

    public DataTable get(String name) {
        if (name == null) return null;
        for (DataTable t : tables) {
            if (t.TableName().equals(name)) return t;
        }
        for (DataTable t : tables) {
            if (t.TableName().equalsIgnoreCase(name)) return t;
        }
        return null;
    }

    public boolean Contains(String name) {
        return get(name) != null;
    }

    public int IndexOf(DataTable table) {
        for (int i = 0; i < tables.size(); i++) {
            if (tables.get(i) == table) return i;
        }
        return -1;
    }

    /** First free {@code Table}, {@code Table1}, {@code Table2}, … */
    String nextName() {
        if (get("Table") == null) return "Table";
        for (int k = 1; ; k++) {
            if (get("Table" + k) == null) return "Table" + k;
        }
    }

    public DataTable Add() {
        DataTable t = new DataTable(nextName());
        Add(t);
        return t;
    }

    public DataTable Add(String name) {
        DataTable t = new DataTable(name);
        Add(t);
        return t;
    }

    public void Add(DataTable table) {
        if (table.dataSet == dataSet) {
            throw new ArgumentException("DataTable already belongs to this DataSet.");
        }
        if (table.dataSet != null) {
            throw new ArgumentException("DataTable already belongs to another DataSet.");
        }
        if (table.TableName().isEmpty()) {
            table.TableName(nextName());
        } else if (get(table.TableName()) != null
                && get(table.TableName()).TableName().equalsIgnoreCase(table.TableName())) {
            throw new DuplicateNameException("A DataTable named '" + table.TableName() + "' already belongs to this DataSet.");
        }
        table.dataSet = dataSet;
        tables.add(table);
    }

    public void Remove(DataTable table) {
        if (tables.remove(table)) {
            table.dataSet = null;
        }
    }

    public void Clear() {
        for (DataTable t : tables) t.dataSet = null;
        tables.clear();
    }

    @Override
    public Iterator<DataTable> iterator() {
        return Collections.unmodifiableList(tables).iterator();
    }
}
