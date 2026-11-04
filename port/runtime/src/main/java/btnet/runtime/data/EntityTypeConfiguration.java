package btnet.runtime.data;

import btnet.runtime.ArgumentException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EF6 {@code System.Data.Entity.ModelConfiguration.EntityTypeConfiguration<T>} as metadata
 * for the runtime {@link DbSet} (RULEBOOK §2A EF6). Lambdas become field-name strings:
 * {@code this.Property(t => t.Id).HasColumnName("qu_id")} →
 * {@code this.Property("Id").HasColumnName("qu_id");}, {@code this.HasKey(t => t.Id)} →
 * {@code this.HasKey("Id")}. {@code IsRequired}, {@code HasMaxLength}, {@code IsFixedLength},
 * {@code HasDatabaseGeneratedOption} are recorded metadata (no effect on queries).
 * <p>
 * The nested {@link DatabaseGeneratedOption} enum is inherited by every map, so
 * {@code HasDatabaseGeneratedOption(DatabaseGeneratedOption.None)} compiles without an import.
 */
public abstract class EntityTypeConfiguration<T> {

    /** EF6 {@code System.ComponentModel.DataAnnotations.Schema.DatabaseGeneratedOption}. */
    public enum DatabaseGeneratedOption { None, Identity, Computed }

    /** EF6 {@code PrimitivePropertyConfiguration} (fluent; every setter returns {@code this}). */
    public static final class PropertyConfiguration {
        final String propertyName;
        String columnName;
        boolean required;
        Integer maxLength;
        boolean fixedLength;
        DatabaseGeneratedOption generatedOption;

        PropertyConfiguration(String propertyName) {
            this.propertyName = propertyName;
        }

        public PropertyConfiguration HasColumnName(String name) {
            this.columnName = name;
            return this;
        }

        public PropertyConfiguration IsRequired() {
            this.required = true;
            return this;
        }

        public PropertyConfiguration IsOptional() {
            this.required = false;
            return this;
        }

        public PropertyConfiguration HasMaxLength(Integer value) {
            this.maxLength = value;
            return this;
        }

        public PropertyConfiguration IsMaxLength() {
            this.maxLength = null;
            return this;
        }

        public PropertyConfiguration IsFixedLength() {
            this.fixedLength = true;
            return this;
        }

        public PropertyConfiguration IsUnicode(boolean unicode) {
            return this;
        }

        public PropertyConfiguration HasDatabaseGeneratedOption(DatabaseGeneratedOption option) {
            this.generatedOption = option;
            return this;
        }

        public String ColumnName() {
            return columnName != null ? columnName : propertyName;
        }
    }

    private final Map<String, PropertyConfiguration> properties = new LinkedHashMap<>();
    private final List<String> keys = new ArrayList<>();
    private String tableName;

    protected EntityTypeConfiguration() {}

    public EntityTypeConfiguration<T> HasKey(String... propertyNames) {
        keys.clear();
        for (String p : propertyNames) keys.add(p);
        return this;
    }

    public PropertyConfiguration Property(String propertyName) {
        return properties.computeIfAbsent(propertyName, PropertyConfiguration::new);
    }

    public EntityTypeConfiguration<T> ToTable(String tableName) {
        this.tableName = tableName;
        return this;
    }

    public EntityTypeConfiguration<T> ToTable(String tableName, String schemaName) {
        this.tableName = schemaName == null || schemaName.isEmpty() ? tableName : schemaName + "." + tableName;
        return this;
    }

    /** {@code Ignore(t => t.X)} → {@code Ignore("X")}: the property is not mapped. */
    public EntityTypeConfiguration<T> Ignore(String propertyName) {
        ignored.add(propertyName);
        return this;
    }

    private final java.util.Set<String> ignored = new java.util.HashSet<>();

    // ------------------------------------------------------------------ metadata used by DbSet

    /** The entity class {@code T} (from the generic superclass of the concrete map). */
    Class<?> entityClass() {
        Type t = getClass().getGenericSuperclass();
        Class<?> c = getClass();
        while (!(t instanceof ParameterizedType pt && pt.getRawType() == EntityTypeConfiguration.class)) {
            c = c.getSuperclass();
            if (c == null || c == Object.class) {
                throw new ArgumentException("Cannot determine the entity type of " + getClass().getName() + ".");
            }
            t = c.getGenericSuperclass();
        }
        Type arg = ((ParameterizedType) t).getActualTypeArguments()[0];
        if (arg instanceof Class<?> k) return k;
        if (arg instanceof ParameterizedType p && p.getRawType() instanceof Class<?> k) return k;
        throw new ArgumentException("Cannot determine the entity type of " + getClass().getName() + ".");
    }

    String tableName() {
        return tableName;
    }

    List<String> keys() {
        return keys;
    }

    boolean isIgnored(String propertyName) {
        return ignored.contains(propertyName);
    }

    String columnFor(String propertyName) {
        PropertyConfiguration pc = properties.get(propertyName);
        return pc == null ? propertyName : pc.ColumnName();
    }
}
