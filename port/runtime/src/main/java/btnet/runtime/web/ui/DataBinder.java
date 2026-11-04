package btnet.runtime.web.ui;

import btnet.runtime.Convert;
import btnet.runtime.DBNull;
import btnet.runtime.Strings;
import btnet.runtime.data.DataRow;
import btnet.runtime.data.DataRowView;
import btnet.runtime.data.DataSet;
import btnet.runtime.data.DataTable;
import btnet.runtime.data.DataView;
import btnet.runtime.web.HttpException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;

/**
 * Internal stand-in for {@code System.Web.UI.DataBinder} and {@code DataSourceHelper.GetResolvedDataSource}
 * as used by {@link ListControl}, {@link HtmlSelect} and {@link DataGrid}.
 */
final class DataBinder {
    private DataBinder() {}

    /**
     * Resolves a {@code DataSource} the way .NET list controls do: {@code DataView} as is, a
     * {@code DataTable} through its {@code DefaultView}, a {@code DataSet} through its first table
     * (no {@code DataMember} is ever set in the source), any {@code Iterable} or array as is.
     * {@code null} → {@code null}.
     */
    static Iterable<?> resolve(Object dataSource) {
        if (dataSource == null) return null;
        if (dataSource instanceof DataView dv) return dv;
        if (dataSource instanceof DataTable dt) return dt.DefaultView();
        if (dataSource instanceof DataSet ds) return ds.Tables().get(0).DefaultView();
        if (dataSource instanceof Iterable<?> it) return it;
        if (dataSource instanceof Object[] arr) return Arrays.asList(arr);
        throw new HttpException("Data source is an invalid type.  It must be either an IListSource, IEnumerable, or IDataSource.");
    }

    /** {@code DataBinder.GetPropertyValue(container, propName)}. */
    static Object eval(Object container, String field) {
        if (container == null) {
            throw new btnet.runtime.ArgumentNullException("container");
        }
        if (container instanceof DataRowView drv) return drv.get(field);
        if (container instanceof DataRow dr) return dr.get(field);
        if (container instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() instanceof String k && k.equalsIgnoreCase(field)) return e.getValue();
            }
            throw notFound(container, field);
        }
        Class<?> c = container.getClass();
        // C# auto-properties are public fields in the port (§2A); computed properties are methods X()
        for (Field f : c.getFields()) {
            if (f.getName().equals(field) && !Modifier.isStatic(f.getModifiers())) {
                try { return f.get(container); } catch (IllegalAccessException e) { throw notFound(container, field); }
            }
        }
        for (String name : new String[] {field, "get" + field}) {
            try {
                Method m = c.getMethod(name);
                if (!Modifier.isStatic(m.getModifiers()) && m.getReturnType() != void.class) {
                    m.setAccessible(true);
                    return m.invoke(container);
                }
            } catch (NoSuchMethodException ignored) {
                // try next
            } catch (ReflectiveOperationException e) {
                throw new HttpException("DataBinding: '" + c.getName() + "' does not contain a property with the name '" + field + "'.");
            }
        }
        throw notFound(container, field);
    }

    /**
     * {@code DataBinder.GetPropertyValue(container, propName, format)}: {@code null}/{@code DBNull} →
     * {@code ""}; no format → {@code Convert.ToString}; else {@code String.Format(format, value)}.
     */
    static String evalString(Object container, String field, String format) {
        Object v = eval(container, field);
        return format(v, format);
    }

    static String format(Object v, String format) {
        if (v == null || v instanceof DBNull) return "";
        if (format == null || format.isEmpty()) return Convert.ToString(v);
        return Strings.Format(format, v);
    }

    private static HttpException notFound(Object container, String field) {
        return new HttpException("DataBinding: '" + container.getClass().getName()
                + "' does not contain a property with the name '" + field + "'.");
    }
}
