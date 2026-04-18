package com.dbpilot.infrastructure.adapter.db;

import com.dbpilot.core.model.DatabaseConnectionInfo;
import com.dbpilot.core.model.QueryResult;
import com.dbpilot.core.model.TableMetadata;
import com.dbpilot.core.port.out.DatabaseAdapter;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for all JDBC-based database adapters.
 *
 * <p>Provides common functionality:</p>
 * <ul>
 *   <li>HikariCP connection pool management (one pool per database alias)</li>
 *   <li>Generic query execution with result set mapping</li>
 *   <li>Virtual Thread-friendly I/O (uses blocking JDBC — ideal for virtual threads)</li>
 *   <li>Error mapping delegation to {@link com.dbpilot.infrastructure.error.DatabaseErrorMapper}</li>
 * </ul>
 *
 * <p>Subclasses must implement:</p>
 * <ul>
 *   <li>{@link #introspectSchema(DatabaseConnectionInfo)} — engine-specific catalog queries</li>
 *   <li>{@link #buildExplainQuery(String)} — engine-specific EXPLAIN syntax</li>
 *   <li>{@link #getDatabaseType()} — enum identity</li>
 * </ul>
 *
 * @author DB-Pilot
 */
@Slf4j
public abstract class AbstractJdbcDatabaseAdapter implements DatabaseAdapter {

    /** Pool cache: alias → DataSource. Thread-safe. */
    private final ConcurrentHashMap<String, HikariDataSource> poolCache = new ConcurrentHashMap<>();

    @Override
    public QueryResult executeQuery(DatabaseConnectionInfo connectionInfo, String query) {
        Instant start = Instant.now();
        try (Connection conn = getConnection(connectionInfo);
             Statement stmt = conn.createStatement()) {

            boolean hasResultSet = stmt.execute(query);
            List<String> columnNames = new ArrayList<>();
            List<Map<String, Object>> rows = new ArrayList<>();
            int rowCount = 0;

            if (hasResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    columnNames = extractColumnNames(rs.getMetaData());
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (String col : columnNames) {
                            row.put(col, rs.getObject(col));
                        }
                        rows.add(row);
                    }
                    rowCount = rows.size();
                }
            } else {
                rowCount = stmt.getUpdateCount();
            }

            Duration elapsed = Duration.between(start, Instant.now());
            log.debug("Executed query on '{}' — {} rows affected/returned in {}", connectionInfo.getAlias(), rowCount, elapsed);

            return QueryResult.builder()
                    .query(query)
                    .columnNames(columnNames)
                    .rows(rows)
                    .rowCount(rows.size())
                    .executionTime(elapsed)
                    .dryRun(false)
                    .build();

        } catch (SQLException e) {
            Duration elapsed = Duration.between(start, Instant.now());
            log.error("Query execution failed on '{}': {}", connectionInfo.getAlias(), e.getMessage());
            return QueryResult.builder()
                    .query(query)
                    .columnNames(List.of())
                    .rows(List.of())
                    .rowCount(0)
                    .executionTime(elapsed)
                    .dryRun(false)
                    .errorMessage(mapError(e))
                    .build();
        }
    }

    @Override
    public QueryResult explainQuery(DatabaseConnectionInfo connectionInfo, String query) {
        String explainSql = buildExplainQuery(query);
        Instant start = Instant.now();

        try (Connection conn = getConnection(connectionInfo);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(explainSql)) {

            StringBuilder plan = new StringBuilder();
            while (rs.next()) {
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    plan.append(rs.getString(i)).append('\n');
                }
            }

            Duration elapsed = Duration.between(start, Instant.now());
            return QueryResult.builder()
                    .query(query)
                    .columnNames(List.of())
                    .rows(List.of())
                    .rowCount(0)
                    .executionTime(elapsed)
                    .dryRun(true)
                    .explainPlan(plan.toString())
                    .build();

        } catch (SQLException e) {
            Duration elapsed = Duration.between(start, Instant.now());
            return QueryResult.builder()
                    .query(query)
                    .executionTime(elapsed)
                    .dryRun(true)
                    .errorMessage("EXPLAIN failed: " + mapError(e))
                    .build();
        }
    }

    @Override
    public boolean testConnection(DatabaseConnectionInfo connectionInfo) {
        try (Connection conn = getConnection(connectionInfo)) {
            return conn.isValid(5);
        } catch (SQLException e) {
            log.warn("Connection test failed for '{}': {}", connectionInfo.getAlias(), e.getMessage());
            return false;
        }
    }

    /**
     * Obtains a JDBC connection from the HikariCP pool.
     *
     * @param connectionInfo the connection parameters
     * @return a pooled JDBC connection
     * @throws SQLException if connection fails
     */
    protected Connection getConnection(DatabaseConnectionInfo connectionInfo) throws SQLException {
        DataSource ds = poolCache.computeIfAbsent(connectionInfo.getAlias(), alias -> {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(connectionInfo.resolveJdbcUrl());
            config.setUsername(connectionInfo.getUsername());
            config.setPassword(connectionInfo.getPassword());
            config.setPoolName("dbpilot-" + alias);
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(10_000);
            config.setIdleTimeout(300_000);
            config.setMaxLifetime(600_000);
            log.info("Created HikariCP pool for '{}'", alias);
            return new HikariDataSource(config);
        });
        return ds.getConnection();
    }

    /**
     * Extracts column names from a ResultSet metadata.
     */
    protected List<String> extractColumnNames(ResultSetMetaData meta) throws SQLException {
        List<String> names = new ArrayList<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            names.add(meta.getColumnLabel(i));
        }
        return names;
    }

    /**
     * Builds the engine-specific EXPLAIN query.
     * Subclasses must override this.
     *
     * @param query the original query to explain
     * @return the EXPLAIN-wrapped query
     */
    protected abstract String buildExplainQuery(String query);

    /**
     * Maps a SQLException to a human-readable error message.
     * Subclasses can override for engine-specific error codes.
     *
     * @param e the SQL exception
     * @return human-readable error hint
     */
    protected String mapError(SQLException e) {
        return "[%s] %s (Error Code: %d, SQLState: %s)".formatted(
                getDatabaseType().getDisplayName(),
                e.getMessage(),
                e.getErrorCode(),
                e.getSQLState());
    }

    /**
     * Cleans up all connection pools. Call on application shutdown.
     */
    public void shutdown() {
        poolCache.forEach((alias, ds) -> {
            log.info("Shutting down HikariCP pool for '{}'", alias);
            ds.close();
        });
        poolCache.clear();
    }
}
