package com.dbpilot.infrastructure.adapter.db;

import com.dbpilot.core.model.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * {@link com.dbpilot.core.port.out.DatabaseAdapter} implementation for MariaDB.
 *
 * <p>MariaDB is highly compatible with MySQL, so this adapter extends
 * {@link MySqlDatabaseAdapter} and overrides only the differences.</p>
 *
 * @author DB-Pilot
 */
@Slf4j
public class MariaDbDatabaseAdapter extends MySqlDatabaseAdapter {

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.MARIADB;
    }

    @Override
    protected String buildExplainQuery(String query) {
        return "EXPLAIN FORMAT=JSON " + query;
    }

    @Override
    protected String mapError(java.sql.SQLException e) {
        // MariaDB uses same error codes as MySQL
        return super.mapError(e).replace("[MySQL]", "[MariaDB]");
    }
}
