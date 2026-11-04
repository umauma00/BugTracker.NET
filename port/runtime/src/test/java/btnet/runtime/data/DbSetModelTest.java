package btnet.runtime.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import btnet.runtime.InvalidOperationException;

import java.util.List;
import org.junit.jupiter.api.Test;

/** DbSet column order never depends on the JVM's unspecified {@code Class.getFields()} order. */
class DbSetModelTest {

    public static class Query {
        public String Sql;
        public int Id;
        public String Name;
        public Integer User;
        public Integer Org;
        public List<String> Navigation;   // not scalar: not mapped
        public static int Ignored;        // static: not mapped
    }

    static final class QueryMap extends EntityTypeConfiguration<Query> {
        QueryMap() {
            HasKey("Id");
            ToTable("queries");
            Property("Id").HasColumnName("qu_id");
            Property("Name").HasColumnName("qu_desc");
            Property("Sql").HasColumnName("qu_sql");
            Ignore("Org");
        }
    }

    public static class Unmapped {
        public String b;
        public int QueryId;
        public String a;
        public int Id;
    }

    @Test
    void configuredPropertiesFirstInRegistrationOrderThenByName() {
        DbSet.EntityModel m = new DbSet.EntityModel(Query.class, new QueryMap());
        assertEquals(List.of("qu_id", "qu_desc", "qu_sql", "User"), m.columns);
        assertEquals(List.of("qu_id"), m.keyColumns);
        assertEquals("queries", m.table);
    }

    @Test
    void withoutConfigurationFieldsAreOrderedByNameAndIdIsTheConventionKey() {
        DbSet.EntityModel m = new DbSet.EntityModel(Unmapped.class, null);
        assertEquals(List.of("Id", "QueryId", "a", "b"), m.columns);
        assertEquals(List.of("Id"), m.keyColumns);
    }

    @Test
    void orderIsStableAcrossBuilds() {
        for (int i = 0; i < 5; i++) {
            assertEquals(new DbSet.EntityModel(Query.class, new QueryMap()).columns,
                    new DbSet.EntityModel(Query.class, new QueryMap()).columns);
        }
    }

    static final class TypoMap extends EntityTypeConfiguration<Query> {
        TypoMap() {
            HasKey("Id");
            Property("Id").HasColumnName("qu_id");
            Property("Nmae").HasColumnName("qu_desc");     // no such field
        }
    }

    static final class CaseMap extends EntityTypeConfiguration<Query> {
        CaseMap() {
            Property("name").HasColumnName("qu_desc");     // field is "Name": exact match required
        }
    }

    static final class NavigationMap extends EntityTypeConfiguration<Query> {
        NavigationMap() {
            Property("Navigation");                        // not a primitive property
        }
    }

    @Test
    void propertyNamingAMissingFieldFailsAtModelBuild() {
        InvalidOperationException e = assertThrows(InvalidOperationException.class,
                () -> new DbSet.EntityModel(Query.class, new TypoMap()));
        assertTrue(e.getMessage().startsWith("The property 'Nmae' is not a declared property on type 'Query'."),
                e.getMessage());
        assertThrows(InvalidOperationException.class, () -> new DbSet.EntityModel(Query.class, new CaseMap()));
        assertThrows(InvalidOperationException.class, () -> new DbSet.EntityModel(Query.class, new NavigationMap()));
    }
}
