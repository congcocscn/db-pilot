package com.dbpilot.core.port.out;

import com.dbpilot.core.model.DatabaseConnectionInfo;
import com.dbpilot.core.model.QueryResult;
import com.dbpilot.core.model.TableMetadata;

import java.util.List;

/**
 * Outbound port for database introspection and query execution.
 *
 * <p>Each supported database engine (Oracle, PostgreSQL, MySQL, MariaDB, MongoDB)
 * must provide an implementation of this interface in the infrastructure layer.
 * Implementations use HikariCP for connection pooling (JDBC engines) or
 * the native MongoDB driver.</p>
 *
 * <p>All I/O operations are designed to be executed on Virtual Threads.</p>
 *
 * @author DB-Pilot
 */
public interface DatabaseAdapter {

    /**
     * Introspects the database schema, returning metadata for all accessible tables.
     *
     * <p>Implementation notes:</p>
     * <ul>
     *   <li>Oracle: queries ALL_TAB_COLUMNS, ALL_CONSTRAINTS, ALL_CONS_COLUMNS</li>
     *   <li>PostgreSQL: queries information_schema.columns, table_constraints</li>
     *   <li>MySQL/MariaDB: queries INFORMATION_SCHEMA</li>
     *   <li>MongoDB: samples documents + listCollections()</li>
     * </ul>
     *
     * @param connectionInfo the database connection parameters
     * @return list of discovered table metadata
     */
    List<TableMetadata> introspectSchema(DatabaseConnectionInfo connectionInfo);

    /**
     * Executes a query against the target database.
     *
     * @param connectionInfo the database connection parameters
     * @param query          the SQL query or MongoDB aggregation pipeline (JSON)
     * @return the query execution result
     */
    QueryResult executeQuery(DatabaseConnectionInfo connectionInfo, String query);

    /**
     * Validates a query using EXPLAIN/EXPLAIN PLAN without executing it.
     *
     * <p>This powers the {@code --dry-run} mode. The implementation should
     * use the database-specific explain mechanism and return the plan in
     * the {@link QueryResult#getExplainPlan()} field.</p>
     *
     * @param connectionInfo the database connection parameters
     * @param query          the query to validate
     * @return the explain plan result with {@code dryRun=true}
     */
    QueryResult explainQuery(DatabaseConnectionInfo connectionInfo, String query);

    /**
     * Tests connectivity to the database.
     *
     * @param connectionInfo the database connection parameters
     * @return {@code true} if the connection is successful
     */
    boolean testConnection(DatabaseConnectionInfo connectionInfo);

    /**
     * Returns the database type this adapter handles.
     *
     * @return the supported database type
     */
    com.dbpilot.core.model.DatabaseType getDatabaseType();
}
