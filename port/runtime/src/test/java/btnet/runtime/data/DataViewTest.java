package btnet.runtime.data;

import btnet.runtime.ArgumentException;
import btnet.runtime.DBNull;
import btnet.runtime.IndexOutOfRangeException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataViewTest {

    private static DataTable bugs() {
        DataTable t = new DataTable("Table");
        t.Columns().Add("id", Integer.class);
        t.Columns().Add("desc", String.class);
        t.Columns().Add("$FLAG", Integer.class);
        t.Rows().Add(1, "beta", 0);
        t.Rows().Add(2, "Alpha", 1);
        t.Rows().Add(3, "alpha", 2);
        t.Rows().Add(4, null, 1);
        return t;
    }

    private static List<Object> ids(DataView dv) {
        List<Object> r = new ArrayList<>();
        for (DataRowView drv : dv) r.add(drv.get("id"));
        return r;
    }

    @Test
    void sortIsCaseInsensitiveStableAndDbNullFirst() {
        // .NET DataTable.CaseSensitive defaults to false: "Alpha" and "alpha" compare equal, so the
        // index keeps table order for them; DBNull sorts before every value.
        DataView dv = new DataView(bugs());
        dv.Sort("desc");
        assertEquals(List.of(4, 2, 3, 1), ids(dv));
        dv.Sort("[desc] DESC");
        assertEquals(List.of(1, 2, 3, 4), ids(dv));
    }

    @Test
    void sortUnknownColumnThrowsIndexOutOfRange() {
        // DataTable.ParseSortString → ExceptionBuilder.ColumnOutOfRange: "Cannot find column x."
        DataView dv = new DataView(bugs());
        IndexOutOfRangeException e = assertThrows(IndexOutOfRangeException.class, () -> dv.Sort("nope ASC"));
        assertEquals("Cannot find column nope.", e.getMessage());
        // "desc XYZ" is not a direction: the whole entry is looked up as a column name
        assertThrows(IndexOutOfRangeException.class, () -> dv.Sort("desc XYZ"));
        assertEquals("", dv.Sort());
    }

    @Test
    void findWithoutSortThrows() {
        // DataView.Find: "Find finds a row based on a Sort order, and no Sort order is specified."
        DataView dv = bugs().DefaultView();
        ArgumentException e = assertThrows(ArgumentException.class, () -> dv.Find(2));
        assertEquals("Find finds a row based on a Sort order, and no Sort order is specified.", e.getMessage());
        dv.Sort("id");
        assertEquals(1, dv.Find(2));
        assertEquals(-1, dv.Find(99));
    }

    @Test
    void bugListFilterForms() {
        // bug_list.js on_filter: "66 = 66  and [col] ='val'", flags rewritten to numbers, tags "and id in (...)"
        DataView dv = new DataView(bugs());
        dv.RowFilter("66 = 66  and [desc] ='ALPHA'");
        assertEquals(List.of(2, 3), ids(dv));
        dv.RowFilter("66 = 66  and [$FLAG] =1");
        assertEquals(List.of(2, 4), ids(dv));
        dv.RowFilter("66 = 66  and [$FLAG] <>0 and id in (1,2,4)");
        assertEquals(List.of(2, 4), ids(dv));
        // a comparison with DBNull is unknown → the row is excluded, also for <>
        dv.RowFilter("[desc]<>'beta'");
        assertEquals(List.of(2, 3), ids(dv));
        dv.RowFilter("bp_type = 'file'".replace("bp_type", "desc"));
        assertEquals(0, dv.Count());
    }

    @Test
    void invalidFilterKeepsPreviousFilter() {
        // build_filter_clause with no matching tag yields "and id in ()": a syntax error in .NET,
        // swallowed by bug_list.cs's catch — the earlier filter stays in effect.
        DataView dv = new DataView(bugs());
        dv.RowFilter("[$FLAG] =1");
        assertThrows(DataView.SyntaxErrorException.class, () -> dv.RowFilter("66 = 66 and id in ()"));
        assertEquals("[$FLAG] =1", dv.RowFilter());
        assertEquals(2, dv.Count());
        // string literal vs int column that cannot be converted → evaluate error
        assertThrows(DataView.EvaluateException.class, () -> dv.RowFilter("[$FLAG] ='red'"));
        assertThrows(UnsupportedOperationException.class, () -> dv.RowFilter("desc like 'a%'"));
    }

    @Test
    void viewFollowsTableChanges() {
        DataTable t = bugs();
        DataView dv = t.DefaultView();
        dv.RowFilter("[$FLAG] = 1");
        assertEquals(2, dv.Count());
        t.Rows().Add(5, "x", 1);
        assertEquals(3, dv.Count());
        assertTrue(dv.get(2).Row() == t.Rows().get(4));
        assertThrows(IndexOutOfRangeException.class, () -> dv.get(3));
    }

    @Test
    void nullStoredAsDbNull() {
        DataTable t = bugs();
        assertEquals(DBNull.Value, t.Rows().get(3).get("desc"));
    }
}
