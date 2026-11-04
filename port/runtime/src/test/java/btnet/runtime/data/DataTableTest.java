package btnet.runtime.data;

import btnet.runtime.ArgumentException;
import btnet.runtime.ClrTypes;
import btnet.runtime.IndexOutOfRangeException;
import btnet.runtime.OverflowException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataTableTest {

    @Test
    void columnLookupCaseInsensitiveExactFirst() {
        DataTable t = new DataTable("users");
        t.Columns().Add("us_id", Integer.class);
        DataRow r = t.NewRow();
        r.set("US_ID", 7);
        assertEquals(7, r.get("us_id"));
        // .NET: "Column 'x' does not belong to table T."
        ArgumentException e = assertThrows(ArgumentException.class, () -> r.get("missing"));
        assertEquals("Column 'missing' does not belong to table users.", e.getMessage());
        assertNull(t.Columns().get("missing"));
        assertThrows(IndexOutOfRangeException.class, () -> r.get(5));
    }

    @Test
    void duplicateColumnNameThrows() {
        // DataColumnCollection: "A column named 'x' already belongs to this DataTable."
        DataTable t = new DataTable();
        t.Columns().Add("file", String.class);
        DuplicateNameException e = assertThrows(DuplicateNameException.class, () -> t.Columns().Add("FILE", String.class));
        assertEquals("A column named 'FILE' already belongs to this DataTable.", e.getMessage());
    }

    @Test
    void fillNamingOfDuplicateAndUnnamedColumns() {
        // SqlDataAdapter schema mapping: a repeated name gets 1, 2, …; unnamed → Column1, … (J-C12)
        DataTable t = new DataTable();
        DataColumnCollection c = t.Columns();
        c.Add(c.uniqueName("us_forced_project"), Integer.class);
        c.Add(c.uniqueName("us_forced_project"), Integer.class);
        c.Add(c.uniqueName(""), Integer.class);
        c.Add(c.uniqueName(""), Integer.class);
        assertEquals("us_forced_project1", c.get(1).ColumnName());
        assertEquals("Column1", c.get(2).ColumnName());
        assertEquals("Column2", c.get(3).ColumnName());
    }

    @Test
    void storingConvertsToColumnType() {
        // DataColumn setter: Convert.ChangeType(v, DataType); failure message per ExceptionBuilder.SetFailed
        DataTable t = new DataTable("Table");
        t.Columns().Add("date", LocalDateTime.class);
        t.Columns().Add("id", Integer.class);
        t.Columns().Add("score", BigDecimal.class);
        t.Columns().Add("tiny", ClrTypes.Byte.class);
        DataRow r = t.NewRow();
        r.set("date", "2026-11-03T10:20:30");
        assertEquals(LocalDateTime.of(2026, 11, 3, 10, 20, 30), r.get("date"));
        r.set("id", "42");
        assertEquals(42, r.get("id"));
        ArgumentException e = assertThrows(ArgumentException.class, () -> r.set("id", "abc"));
        assertEquals(true, e.getMessage().endsWith("Couldn't store <abc> in id Column.  Expected type is Int32."));
        r.set("tiny", 200);
        assertEquals((short) 200, r.get("tiny"));
        assertThrows(ArgumentException.class, () -> r.set("tiny", 300));
        assertThrows(OverflowException.class, () -> DataColumn.changeType(300, ClrTypes.Byte.class));
    }

    @Test
    void tablesAddNamesAndIndexErrors() {
        DataSet ds = new DataSet();
        assertEquals("Table", ds.Tables().Add().TableName());
        assertEquals("Table1", ds.Tables().Add().TableName());
        // DataTableCollection indexer: "Cannot find table 5."
        IndexOutOfRangeException e = assertThrows(IndexOutOfRangeException.class, () -> ds.Tables().get(5));
        assertEquals("Cannot find table 5.", e.getMessage());
        IndexOutOfRangeException e2 = assertThrows(IndexOutOfRangeException.class, () -> ds.Tables().get(0).Rows().get(0));
        assertEquals("There is no row at position 0.", e2.getMessage());
    }
}
