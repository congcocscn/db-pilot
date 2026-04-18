package com.dbpilot.infrastructure.adapter.db;

import com.dbpilot.core.model.DatabaseType;
import com.dbpilot.core.port.out.DatabaseAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.Map;

/**
 * Factory for resolving the correct {@link DatabaseAdapter} based on {@link DatabaseType}.
 *
 * <p>Uses a strategy map pattern. All adapters are registered at construction time
 * and looked up by enum key at runtime.</p>
 *
 * @author DB-Pilot
 */
@Slf4j
public class DatabaseAdapterFactory {

    private final Map<DatabaseType, DatabaseAdapter> adapters = new EnumMap<>(DatabaseType.class);

    /**
     * Creates the factory with all available adapters.
     */
    public DatabaseAdapterFactory() {
        register(new PostgresDatabaseAdapter());
        register(new OracleDatabaseAdapter());
        register(new MySqlDatabaseAdapter());
        register(new MariaDbDatabaseAdapter());
        register(new MongoDatabaseAdapter());
        log.info("DatabaseAdapterFactory initialized with {} adapters", adapters.size());
    }

    /**
     * Resolves the adapter for a given database type.
     *
     * @param type the database type
     * @return the matching adapter
     * @throws IllegalArgumentException if no adapter is registered for the type
     */
    public DatabaseAdapter getAdapter(DatabaseType type) {
        DatabaseAdapter adapter = adapters.get(type);
        if (adapter == null) {
            throw new IllegalArgumentException("No adapter registered for database type: " + type);
        }
        return adapter;
    }

    /**
     * Registers an adapter for its declared database type.
     *
     * @param adapter the adapter to register
     */
    public void register(DatabaseAdapter adapter) {
        adapters.put(adapter.getDatabaseType(), adapter);
        log.debug("Registered adapter for {}", adapter.getDatabaseType().getDisplayName());
    }
}
