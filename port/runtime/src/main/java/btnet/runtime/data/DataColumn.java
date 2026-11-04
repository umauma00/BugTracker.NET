package btnet.runtime.data;

import btnet.runtime.ArgumentException;
import btnet.runtime.ClrTypes;
import btnet.runtime.Convert;
import btnet.runtime.CultureInfo;
import btnet.runtime.DBNull;
import btnet.runtime.DateTimes;
import btnet.runtime.Exceptions;
import btnet.runtime.InvalidCastException;
import btnet.runtime.OverflowException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * .NET {@code System.Data.DataColumn}. {@link #DataType()} is the .NET column type as a
 * {@code Class<?>} token (RULEBOOK §2A {@code typeof} table; tinyint → {@code ClrTypes.Byte.class}
 * holding {@code Short} values). Default type {@code String}.
 * <p>
 * Writing a value into the column (RULEBOOK §2A [J-C11]): {@code null} → {@code DBNull.Value};
 * a value of another class is converted as .NET {@code Convert.ChangeType(v, DataType, en-US)};
 * String → DateTime goes through {@code DateTimes.Parse}; failure →
 * {@code ArgumentException("<inner message>Couldn't store <v> in <col> Column.  Expected type is <T>.")}.
 */
public class DataColumn {
    private String columnName = "";
    private Class<?> dataType = String.class;
    DataTable table;
    private int maxLength = -1;
    private boolean allowDBNull = true;
    private Object defaultValue = DBNull.Value;
    private String caption;

    public DataColumn() {}

    public DataColumn(String columnName) {
        this.columnName = columnName == null ? "" : columnName;
    }

    public DataColumn(String columnName, Class<?> dataType) {
        this(columnName);
        if (dataType == null) {
            throw new ArgumentException("Value cannot be null.\r\nParameter name: dataType");
        }
        this.dataType = boxed(dataType);
    }

    public String ColumnName() { return columnName; }

    public void ColumnName(String value) {
        String v = value == null ? "" : value;
        if (table != null && !v.equalsIgnoreCase(columnName)) {
            DataColumn other = table.Columns().find(v);
            if (other != null && other != this) {
                throw new DuplicateNameException("A column named '" + v + "' already belongs to this DataTable.");
            }
        }
        this.columnName = v;
    }

    public Class<?> DataType() { return dataType; }

    public void DataType(Class<?> value) { this.dataType = boxed(value); }

    public int MaxLength() { return maxLength; }

    public void MaxLength(int value) { this.maxLength = value; }

    public boolean AllowDBNull() { return allowDBNull; }

    public void AllowDBNull(boolean value) { this.allowDBNull = value; }

    public Object DefaultValue() { return defaultValue; }

    public void DefaultValue(Object value) { this.defaultValue = value == null ? DBNull.Value : coerce(this, value); }

    public String Caption() { return caption != null ? caption : columnName; }

    public void Caption(String value) { this.caption = value; }

    /** Position in the owning table's columns, -1 when detached. */
    public int Ordinal() { return table == null ? -1 : table.Columns().IndexOf(this); }

    public DataTable Table() { return table; }

    @Override
    public String toString() { return columnName; }

    private static Class<?> boxed(Class<?> c) {
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == short.class) return Short.class;
        if (c == boolean.class) return Boolean.class;
        if (c == double.class) return Double.class;
        if (c == float.class) return Float.class;
        if (c == byte.class) return ClrTypes.Byte.class;
        if (c == char.class) return Character.class;
        return c;
    }

    /** Converts {@code v} for storage in {@code col} (see class comment). */
    static Object coerce(DataColumn col, Object v) {
        if (v == null || v == DBNull.Value) {
            return DBNull.Value;
        }
        Class<?> t = col.dataType;
        if (t == Object.class) return v;
        if (t == ClrTypes.Byte.class) {
            if (v instanceof Short s && s >= 0 && s <= 255) return v;
        } else if (t.isInstance(v)) {
            return v;
        }
        try {
            return changeType(v, t);
        } catch (RuntimeException e) {
            throw new ArgumentException(Exceptions.Message(e) + "Couldn't store <" + Convert.ToString(v) + "> in "
                    + col.columnName + " Column.  Expected type is " + SqlBatch.clrTypeName(t) + ".");
        }
    }

    /** .NET {@code Convert.ChangeType(v, t, en-US)} for the column types the port uses. */
    static Object changeType(Object v, Class<?> t) {
        if (t == String.class) return Convert.ToString(v);
        if (t == Integer.class) return Convert.ToInt32(v);
        if (t == Long.class) return Convert.ToInt64(v);
        if (t == Short.class) {
            int i = Convert.ToInt32(v);
            if (i < Short.MIN_VALUE || i > Short.MAX_VALUE) {
                throw new OverflowException("Value was either too large or too small for an Int16.");
            }
            return (short) i;
        }
        if (t == ClrTypes.Byte.class) {
            int i = Convert.ToInt32(v);
            if (i < 0 || i > 255) {
                throw new OverflowException("Value was either too large or too small for an unsigned byte.");
            }
            return (short) i;
        }
        if (t == Boolean.class) return Convert.ToBoolean(v);
        if (t == BigDecimal.class) return Convert.ToDecimal(v);
        if (t == Double.class) return toDouble(v);
        if (t == Float.class) return (float) toDouble(v);
        if (t == LocalDateTime.class) {
            if (v instanceof String s) return DateTimes.Parse(s, CultureInfo.EN_US);
            throw new InvalidCastException("Invalid cast from '" + SqlBatch.clrTypeName(v.getClass()) + "' to 'DateTime'.");
        }
        if (t == UUID.class) {
            if (v instanceof String s) {
                try {
                    return UUID.fromString(s.trim());
                } catch (IllegalArgumentException e) {
                    throw new btnet.runtime.FormatException("Guid should contain 32 digits with 4 dashes (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx).");
                }
            }
        }
        if (t == Duration.class && v instanceof String s) {
            throw new InvalidCastException("Invalid cast from 'String' to 'TimeSpan'.");
        }
        throw new InvalidCastException("Invalid cast from '" + SqlBatch.clrTypeName(v.getClass()) + "' to '"
                + SqlBatch.clrTypeName(t) + "'.");
    }

    /** .NET {@code Convert.ToDouble(object)} (en-US): numbers widen, strings parse, bool → 1/0. */
    static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean b) return b ? 1.0 : 0.0;
        if (v instanceof String s) {
            String x = s.trim().replace(",", "");
            try {
                if (x.isEmpty() || x.endsWith("d") || x.endsWith("D") || x.endsWith("f") || x.endsWith("F")) {
                    throw new NumberFormatException();
                }
                return Double.parseDouble(x);
            } catch (NumberFormatException e) {
                throw new btnet.runtime.FormatException("Input string was not in a correct format.");
            }
        }
        throw new InvalidCastException("Invalid cast from '" + SqlBatch.clrTypeName(v.getClass()) + "' to 'Double'.");
    }
}
