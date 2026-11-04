package btnet.runtime.data;

import btnet.runtime.InvalidOperationException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EF6 {@code System.Data.Entity.DbContext} over the runtime ADO.NET facade (RULEBOOK §2A EF6,
 * §3 [S1-30]). The translated {@code Context extends DbContext} declares its
 * {@code public DbSet<X> Xs;} fields <b>without initializers</b>; this constructor runs the
 * subclass's {@code OnModelCreating(DbModelBuilder)} once per context type (cached, as EF caches
 * the model) and assigns every public {@code DbSet<X>} field from the registered
 * {@link EntityTypeConfiguration}s. {@code new Context()} (connection name
 * {@code "Name=bugtrackerContext"}) uses the shared DataSource ({@code SqlConnection.UseDataSource});
 * each query opens and closes its own connection, as EF does.
 * <p>
 * {@code close()} is declared without {@code throws}, so {@code try (Context c = new Context())}
 * needs no catch. The nested {@link Database} stands for EF6 {@code System.Data.Entity.Database}
 * ({@code Database.SetInitializer<Context>(null)} in a static initializer resolves to it).
 */
public abstract class DbContext implements AutoCloseable {

    /** EF6 {@code System.Data.Entity.Database} (static members the source uses). */
    public static final class Database {
        private Database() {}

        /** No database initialization is ever performed by the port (as with a {@code null} initializer). */
        public static <TContext> void SetInitializer(Object strategy) {
            // nothing: the port never creates or migrates the schema
        }
    }

    private static final Map<Class<?>, Map<Class<?>, DbSet.EntityModel>> MODELS = new ConcurrentHashMap<>();

    private final String nameOrConnectionString;
    private boolean disposed;

    protected DbContext() {
        this(null);
    }

    protected DbContext(String nameOrConnectionString) {
        this.nameOrConnectionString = nameOrConnectionString;
        Map<Class<?>, DbSet.EntityModel> models = MODELS.computeIfAbsent(getClass(), k -> buildModel());
        for (Field f : getClass().getFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType() != DbSet.class) continue;
            Class<?> entity = entityOf(f);
            DbSet.EntityModel m = models.computeIfAbsent(entity, e -> new DbSet.EntityModel(e, null));
            try {
                f.set(this, new DbSet<>(this, m));
            } catch (IllegalAccessException e) {
                throw new InvalidOperationException("Cannot initialize DbSet property '" + f.getName() + "'.");
            }
        }
    }

    private Map<Class<?>, DbSet.EntityModel> buildModel() {
        DbModelBuilder builder = new DbModelBuilder();
        OnModelCreating(builder);
        Map<Class<?>, DbSet.EntityModel> models = new ConcurrentHashMap<>();
        Map<Class<?>, EntityTypeConfiguration<?>> byEntity = new LinkedHashMap<>();
        for (EntityTypeConfiguration<?> c : builder.Configurations().all()) {
            byEntity.put(c.entityClass(), c);
        }
        for (Map.Entry<Class<?>, EntityTypeConfiguration<?>> e : byEntity.entrySet()) {
            models.put(e.getKey(), new DbSet.EntityModel(e.getKey(), e.getValue()));
        }
        return models;
    }

    private static Class<?> entityOf(Field f) {
        Type t = f.getGenericType();
        if (t instanceof ParameterizedType pt && pt.getActualTypeArguments()[0] instanceof Class<?> c) {
            return c;
        }
        throw new InvalidOperationException("DbSet property '" + f.getName() + "' must declare its entity type.");
    }

    /** EF6 {@code OnModelCreating}; overridden by the translated {@code Context}. */
    protected void OnModelCreating(DbModelBuilder modelBuilder) {
    }

    /** A fresh open connection for one query (closed by the caller). */
    SqlConnection openConnection() {
        if (disposed) {
            throw new InvalidOperationException("The operation cannot be completed because the DbContext has been disposed.");
        }
        String cs = nameOrConnectionString;
        SqlConnection conn;
        if (cs == null || cs.regionMatches(true, 0, "name=", 0, 5)) {
            if (SqlConnection.registeredDataSource() == null) {
                String name = cs == null ? getClass().getSimpleName() : cs.substring(5).trim();
                throw new InvalidOperationException("No connection string named '" + name
                        + "' could be found in the application config file.");
            }
            conn = new SqlConnection("");
        } else {
            conn = new SqlConnection(cs);
        }
        if (!conn.isOpen()) {
            conn.Open();
        }
        return conn;
    }

    public void Dispose() {
        disposed = true;
    }

    @Override
    public void close() {
        Dispose();
    }
}
