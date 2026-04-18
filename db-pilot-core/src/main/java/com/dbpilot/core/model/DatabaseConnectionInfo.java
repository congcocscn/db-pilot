package com.dbpilot.core.model;

import lombok.Builder;
import lombok.Value;

/**
 * Value Object representing database connection parameters.
 *
 * <p>Immutable by design — once a connection is configured, its parameters
 * should not change during the lifecycle of a request.</p>
 *
 * @author DB-Pilot
 */
@Value
@Builder
public class DatabaseConnectionInfo {

    /** Unique alias for this connection (e.g., "prod-postgres", "analytics-oracle"). */
    String alias;

    /** Database engine type. */
    DatabaseType databaseType;

    /** Hostname or IP address. */
    String host;

    /** Port number. */
    int port;

    /** Database name (or SID/service name for Oracle). */
    String databaseName;

    /** Schema to introspect (nullable — uses default schema if omitted). */
    String schema;

    /** Username for authentication. */
    String username;

    /** Password for authentication (transient — not logged). */
    @lombok.ToString.Exclude
    String password;

    /** Optional JDBC URL override (takes precedence over host/port/databaseName). */
    String jdbcUrl;

    /**
     * Builds the JDBC URL from individual components if not explicitly provided.
     *
     * @return a fully-formed JDBC connection string
     */
    public String resolveJdbcUrl() {
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            return jdbcUrl;
        }
        return switch (databaseType) {
            case ORACLE -> String.format("jdbc:oracle:thin:@//%s:%d/%s", host, port, databaseName);
            case POSTGRESQL -> String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName);
            case MYSQL -> String.format("jdbc:mysql://%s:%d/%s", host, port, databaseName);
            case MARIADB -> String.format("jdbc:mariadb://%s:%d/%s", host, port, databaseName);
            case MONGODB -> String.format("mongodb://%s:%d/%s", host, port, databaseName);
        };
    }
}
