package com.dbpilot.infrastructure.adapter.db;

import com.dbpilot.core.model.DatabaseConnectionInfo;
import com.dbpilot.core.model.QueryResult;
import com.dbpilot.core.model.TableMetadata;
import com.dbpilot.core.port.out.DatabaseAdapter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * A routing adapter that implements the generic DatabaseAdapter port
 * but dynamically delegates calls to the correct engine-specific adapter
 * based on the connection info's database type.
 */
@RequiredArgsConstructor
public class RoutingDatabaseAdapter implements DatabaseAdapter {

    private final DatabaseAdapterFactory factory;

    @Override
    public com.dbpilot.core.model.DatabaseType getDatabaseType() {
        throw new UnsupportedOperationException("RoutingDatabaseAdapter is a dispatcher and does not have a specific DatabaseType");
    }

    @Override
    public List<TableMetadata> introspectSchema(DatabaseConnectionInfo connectionInfo) {
        return factory.getAdapter(connectionInfo.getDatabaseType()).introspectSchema(connectionInfo);
    }

    @Override
    public QueryResult executeQuery(DatabaseConnectionInfo connectionInfo, String query) {
        return factory.getAdapter(connectionInfo.getDatabaseType()).executeQuery(connectionInfo, query);
    }

    @Override
    public QueryResult explainQuery(DatabaseConnectionInfo connectionInfo, String query) {
        return factory.getAdapter(connectionInfo.getDatabaseType()).explainQuery(connectionInfo, query);
    }

    @Override
    public boolean testConnection(DatabaseConnectionInfo connectionInfo) {
        return factory.getAdapter(connectionInfo.getDatabaseType()).testConnection(connectionInfo);
    }
}
