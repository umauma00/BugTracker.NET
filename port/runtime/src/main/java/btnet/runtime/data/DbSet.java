package btnet.runtime.data;

import btnet.runtime.ArgumentException;
import btnet.runtime.ClrTypes;
import btnet.runtime.DBNull;
import btnet.runtime.InvalidOperationException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * EF6 {@code System.Data.Entity.DbSet<T>} over JDBC, with exactly the query surface the source
 * uses (RULEBOOK §2A EF6):
 * <ul>
 * <li>{@code Find(key)} → {@code SELECT <all mapped columns> FROM <table> WHERE <key> = @p0}
 *   (explicit column list, so a missing column fails as EF did); not found → {@code null}.</li>
 * <li>{@code Where(String sqlPredicate, SqlParameter...)} — predicate written over mapped
 *   column names with EF6 null semantics, e.g. {@code "(qu_user is null or qu_user = @userId)"}.</li>
 * <li>{@code OrderBy("Name")} / {@code OrderByDescending} / {@code ThenBy} — ORDER BY the
 *   mapped column in SQL (collation ordering, never in memory).</li>
 * <li>{@code ToList()} — runs the one SELECT (WHERE then ORDER BY) and materializes.</li>
 * </ul>
 * Each query method returns a new immutable query; chains compose in source order.
 * Materialization is strict like EF6: a SQL NULL into a non-nullable (primitive) property, or
 * a value whose .NET type differs from the property type (e.g. smallint into {@code int}),
 * throws {@code InvalidOperationException("The 'P' property on 'E' could not be set to …")}.
 * Mapped properties = public instance fields of a scalar type (navigation properties are
 * never loaded).
 */
public class DbSet<T> {

    /** Mapping of one entity type (built once per context type). */
    static final class EntityModel {
        final Class<?> type;
        final String table;
        final List<Field> fields = new ArrayList<>();
        final List<String> columns = new ArrayList<>();
        final List<Field> keyFields = new ArrayList<>();
        final List<String> keyColumns = new ArrayList<>();
        final EntityTypeConfiguration<?> config;

        EntityModel(Class<?> type, EntityTypeConfiguration<?> config) {
            this.type = type;
            this.config = config;
            String t = config != null ? config.tableName() : null;
            // TODO(port): EF6 convention table name (pluralized) when a map has no ToTable — none in source
            this.table = t != null ? t : type.getSimpleName();
            for (Field f : type.getFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !isScalar(f.getType())) continue;
                if (config != null && config.isIgnored(f.getName())) continue;
                fields.add(f);
                columns.add(config != null ? config.columnFor(f.getName()) : f.getName());
            }
            List<String> keys = config != null ? config.keys() : List.of();
            if (keys.isEmpty()) {
                for (Field f : fields) {
                    if (f.getName().equalsIgnoreCase("Id") || f.getName().equalsIgnoreCase(type.getSimpleName() + "Id")) {
                        keys = List.of(f.getName());
                        break;
                    }
                }
            }
            for (String k : keys) {
                int i = indexOfField(k);
                if (i < 0) {
                    throw new InvalidOperationException("The key component '" + k + "' is not a declared property on type '"
                            + type.getSimpleName() + "'.");
                }
                keyFields.add(fields.get(i));
                keyColumns.add(columns.get(i));
            }
        }

        int indexOfField(String name) {
            for (int i = 0; i < fields.size(); i++) {
                if (fields.get(i).getName().equals(name)) return i;
            }
            return -1;
        }

        String column(String property) {
            int i = indexOfField(property);
            if (i < 0) {
                throw new ArgumentException("The property '" + property + "' is not a mapped property of '"
                        + type.getSimpleName() + "'.");
            }
            return columns.get(i);
        }
    }

    private static final Set<Class<?>> SCALARS = Set.of(String.class, Integer.class, int.class, Long.class, long.class,
            Short.class, short.class, Boolean.class, boolean.class, Double.class, double.class, Float.class, float.class,
            BigDecimal.class, LocalDateTime.class, byte[].class, UUID.class, Duration.class);

    static boolean isScalar(Class<?> c) {
        return SCALARS.contains(c);
    }

    private final DbContext context;
    private final EntityModel model;
    private final List<String> predicates;
    private final List<SqlParameter> parameters;
    private final List<String> ordering;

    DbSet(DbContext context, EntityModel model) {
        this(context, model, List.of(), List.of(), List.of());
    }

    private DbSet(DbContext context, EntityModel model, List<String> predicates, List<SqlParameter> parameters,
                  List<String> ordering) {
        this.context = context;
        this.model = model;
        this.predicates = predicates;
        this.parameters = parameters;
        this.ordering = ordering;
    }

    private static <E> List<E> plus(List<E> list, List<E> more) {
        List<E> r = new ArrayList<>(list);
        r.addAll(more);
        return Collections.unmodifiableList(r);
    }

    public DbSet<T> Where(String sqlPredicate, SqlParameter... sqlParameters) {
        return new DbSet<>(context, model, plus(predicates, List.of(sqlPredicate)),
                plus(parameters, sqlParameters == null ? List.of() : List.of(sqlParameters)), ordering);
    }

    /** Replaces any earlier ordering (LINQ {@code OrderBy}). */
    public DbSet<T> OrderBy(String property) {
        return new DbSet<>(context, model, predicates, parameters, List.of("[" + model.column(property) + "] ASC"));
    }

    public DbSet<T> OrderByDescending(String property) {
        return new DbSet<>(context, model, predicates, parameters, List.of("[" + model.column(property) + "] DESC"));
    }

    public DbSet<T> ThenBy(String property) {
        return new DbSet<>(context, model, predicates, parameters, plus(ordering, List.of("[" + model.column(property) + "] ASC")));
    }

    public DbSet<T> ThenByDescending(String property) {
        return new DbSet<>(context, model, predicates, parameters, plus(ordering, List.of("[" + model.column(property) + "] DESC")));
    }

    /** The SELECT this query runs. */
    public String ToSql() {
        StringBuilder sb = new StringBuilder("SELECT ");
        for (int i = 0; i < model.columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append('[').append(model.columns.get(i)).append(']');
        }
        sb.append("\nFROM ").append(bracketTable(model.table));
        for (int i = 0; i < predicates.size(); i++) {
            sb.append(i == 0 ? "\nWHERE (" : " AND (").append(predicates.get(i)).append(')');
        }
        if (!ordering.isEmpty()) {
            sb.append("\nORDER BY ").append(String.join(", ", ordering));
        }
        return sb.toString();
    }

    private static String bracketTable(String t) {
        StringBuilder sb = new StringBuilder();
        for (String part : t.split("\\.")) {
            if (sb.length() > 0) sb.append('.');
            sb.append('[').append(part).append(']');
        }
        return sb.toString();
    }

    public List<T> ToList() {
        return run(ToSql(), parameters, Integer.MAX_VALUE);
    }

    public T FirstOrDefault() {
        List<T> r = ToList();
        return r.isEmpty() ? null : r.get(0);
    }

    public int Count() {
        return ToList().size();
    }

    /**
     * EF6 {@code Find}: one value per key property, each of the key's type (else
     * {@code ArgumentException}); returns the entity or {@code null}.
     */
    public T Find(Object... keyValues) {
        if (keyValues == null || keyValues.length != model.keyFields.size()) {
            throw new ArgumentException("The number of primary key values passed must match number of primary key values defined on the entity.\r\nParameter name: keyValues");
        }
        List<SqlParameter> ps = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        for (int i = 0; i < keyValues.length; i++) {
            Object v = keyValues[i];
            if (v == null) {
                return null;
            }
            Class<?> kt = boxed(model.keyFields.get(i).getType());
            if (!kt.isInstance(v)) {
                throw new ArgumentException("The type of one of the primary key values did not match the type defined in the entity. See inner exception for details.\r\nParameter name: keyValues");
            }
            if (i > 0) where.append(" AND ");
            where.append('[').append(model.keyColumns.get(i)).append("] = @p").append(i);
            ps.add(new SqlParameter("@p" + i, v));
        }
        StringBuilder sb = new StringBuilder("SELECT TOP (2) ");
        for (int i = 0; i < model.columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append('[').append(model.columns.get(i)).append(']');
        }
        sb.append("\nFROM ").append(bracketTable(model.table)).append("\nWHERE ").append(where);
        List<T> r = run(sb.toString(), ps, 2);
        if (r.size() > 1) {
            throw new InvalidOperationException("Sequence contains more than one element");
        }
        return r.isEmpty() ? null : r.get(0);
    }

    private List<T> run(String sql, List<SqlParameter> ps, int max) {
        List<T> result = new ArrayList<>();
        try (SqlConnection conn = context.openConnection();
             SqlCommand cmd = new SqlCommand(sql, conn)) {
            for (SqlParameter p : ps) {
                SqlParameter copy = new SqlParameter(p.ParameterName(), p.Value());
                if (p.hasExplicitType()) copy.SqlDbType(p.SqlDbType());
                copy.Size(p.Size());
                cmd.Parameters().Add(copy);
            }
            try (SqlDataReader reader = cmd.ExecuteReader()) {
                while (result.size() < max && reader.Read()) {
                    result.add(materialize(reader));
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private T materialize(SqlDataReader reader) {
        Object entity;
        try {
            entity = model.type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new InvalidOperationException("The class '" + model.type.getName() + "' has no parameterless constructor.");
        }
        for (int i = 0; i < model.fields.size(); i++) {
            Field f = model.fields.get(i);
            Object v = reader.GetValue(i);
            Class<?> ft = f.getType();
            if (v == DBNull.Value) {
                if (ft.isPrimitive()) {
                    throw new InvalidOperationException("The '" + f.getName() + "' property on '" + model.type.getSimpleName()
                            + "' could not be set to a 'null' value. You must set this property to a non-null value of type '"
                            + netName(ft) + "'. ");
                }
                v = null;
            } else {
                Class<?> colType = reader.GetFieldType(i);
                boolean ok = boxed(ft).isInstance(v) && colType != ClrTypes.Byte.class;
                if (!ok) {
                    throw new InvalidOperationException("The '" + f.getName() + "' property on '" + model.type.getSimpleName()
                            + "' could not be set to a '" + "System." + SqlBatch.clrTypeName(colType) + "' value. You must set this property to a non-null value of type '"
                            + netName(ft) + "'. ");
                }
            }
            try {
                f.set(entity, v);
            } catch (IllegalAccessException e) {
                throw new InvalidOperationException("Cannot set property '" + f.getName() + "'.");
            }
        }
        return (T) entity;
    }

    private static String netName(Class<?> t) {
        if (t.isPrimitive()) return "System." + SqlBatch.clrTypeName(t);
        if (t == String.class || t == byte[].class) return "System." + SqlBatch.clrTypeName(t);
        return "System.Nullable`1[System." + SqlBatch.clrTypeName(t) + "]";
    }

    static Class<?> boxed(Class<?> c) {
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == short.class) return Short.class;
        if (c == boolean.class) return Boolean.class;
        if (c == double.class) return Double.class;
        if (c == float.class) return Float.class;
        return c;
    }
}
