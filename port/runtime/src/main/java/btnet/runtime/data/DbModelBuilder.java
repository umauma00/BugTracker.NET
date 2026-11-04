package btnet.runtime.data;

import java.util.ArrayList;
import java.util.List;

/**
 * EF6 {@code System.Data.Entity.DbModelBuilder}, passed to {@code DbContext.OnModelCreating}:
 * {@code modelBuilder.Configurations.Add(new QueryMap())} →
 * {@code modelBuilder.Configurations().Add(new QueryMap());}.
 */
public class DbModelBuilder {

    /** EF6 {@code ConfigurationRegistrar}. */
    public static final class ConfigurationRegistrar {
        private final List<EntityTypeConfiguration<?>> configurations = new ArrayList<>();

        public ConfigurationRegistrar Add(EntityTypeConfiguration<?> configuration) {
            configurations.add(configuration);
            return this;
        }

        List<EntityTypeConfiguration<?>> all() {
            return configurations;
        }
    }

    private final ConfigurationRegistrar configurations = new ConfigurationRegistrar();

    public ConfigurationRegistrar Configurations() {
        return configurations;
    }

    /** The configuration registered for {@code entity}, or {@code null}. */
    EntityTypeConfiguration<?> configurationFor(Class<?> entity) {
        for (EntityTypeConfiguration<?> c : configurations.all()) {
            if (c.entityClass() == entity) return c;
        }
        return null;
    }
}
