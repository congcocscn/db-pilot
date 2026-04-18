package com.dbpilot.infrastructure.adapter.db;

import com.dbpilot.core.model.*;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link com.dbpilot.core.port.out.DatabaseAdapter} implementation for PostgreSQL.
 *
 * <p>Schema introspection uses {@code information_schema} views.
 * EXPLAIN uses {@code EXPLAIN (FORMAT TEXT)} for query validation.</p>
 *
 * @author DB-Pilot
 */
@Slf4j
public class PostgresDatabaseAdapter extends AbstractJdbcDatabaseAdapter {

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.POSTGRESQL;
    }

    @Override
    public List<TableMetadata> introspectSchema(DatabaseConnectionInfo connectionInfo) {
        String schema = connectionInfo.getSchema() != null ? connectionInfo.getSchema() : "public";
        List<TableMetadata> tables = new ArrayList<>();

        try (Connection conn = getConnection(connectionInfo)) {
            // 1. Get all tables
            List<String> tableNames = getTableNames(conn, schema);

            for (String tableName : tableNames) {
                // 2. Get columns
                List<ColumnMetadata> columns = getColumns(conn, schema, tableName);

                // 3. Get foreign keys
                List<ForeignKeyRelation> foreignKeys = getForeignKeys(conn, schema, tableName);

                // 4. Get row estimate from pg_stat
                long rowEstimate = getRowEstimate(conn, schema, tableName);

                tables.add(TableMetadata.builder()
                        .tableName(tableName)
                        .schemaName(schema)
                        .columns(columns)
                        .foreignKeys(foreignKeys)
                        .referencedBy(List.of()) // Populated in post-processing
                        .estimatedRowCount(rowEstimate)
                        .build());
            }

            log.info("PostgreSQL introspection for '{}': found {} tables in schema '{}'",
                    connectionInfo.getAlias(), tables.size(), schema);

        } catch (SQLException e) {
            log.error("PostgreSQL schema introspection failed: {}", e.getMessage());
            throw new RuntimeException("Schema introspection failed: " + mapError(e), e);
        }

        return tables;
    }

    @Override
    protected String buildExplainQuery(String query) {
        return "EXPLAIN (FORMAT TEXT) " + query;
    }

    @Override
    protected String mapError(SQLException e) {
        // PostgreSQL-specific error code mapping
        String sqlState = e.getSQLState();
        String hint = switch (sqlState != null ? sqlState : "") {
            case "42P01" -> "Table does not exist. Check the table name and schema.";
            case "42703" -> "Column does not exist. Verify column name spelling.";
            case "42601" -> "SQL syntax error. Check query syntax.";
            case "28P01" -> "Authentication failed. Check username and password.";
            case "3D000" -> "Database does not exist. Verify database name.";
            case "08001" -> "Connection refused. Check if PostgreSQL is running.";
            case "23505" -> "Unique constraint violated. Duplicate key value.";
            case "23503" -> "Foreign key constraint violated.";
            default -> e.getMessage();
        };
        return "[PostgreSQL] %s (SQLState: %s)".formatted(hint, sqlState);
    }

    private List<String> getTableNames(Connection conn, String schema) throws SQLException {
        String sql = """
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = ? AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """;
        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("table_name"));
                }
            }
        }
        return names;
    }

    private List<ColumnMetadata> getColumns(Connection conn, String schema, String table) throws SQLException {
        String sql = """
                SELECT column_name, data_type, character_maximum_length,
                       is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ?
                ORDER BY ordinal_position
                """;

        // Get primary key columns
        List<String> pkColumns = getPrimaryKeyColumns(conn, schema, table);

        List<ColumnMetadata> columns = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String colName = rs.getString("column_name");
                    String dataType = rs.getString("data_type");
                    Integer maxLen = rs.getObject("character_maximum_length", Integer.class);
                    String fullType = maxLen != null ? "%s(%d)".formatted(dataType, maxLen) : dataType;

                    columns.add(ColumnMetadata.builder()
                            .name(colName)
                            .dataType(fullType)
                            .nullable("YES".equals(rs.getString("is_nullable")))
                            .primaryKey(pkColumns.contains(colName))
                            .defaultValue(rs.getString("column_default"))
                            .build());
                }
            }
        }
        return columns;
    }

    private List<String> getPrimaryKeyColumns(Connection conn, String schema, String table) throws SQLException {
        String sql = """
                SELECT kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                  AND tc.table_schema = kcu.table_schema
                WHERE tc.constraint_type = 'PRIMARY KEY'
                  AND tc.table_schema = ? AND tc.table_name = ?
                """;
        List<String> pks = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pks.add(rs.getString("column_name"));
                }
            }
        }
        return pks;
    }

    private List<ForeignKeyRelation> getForeignKeys(Connection conn, String schema, String table) throws SQLException {
        String sql = """
                SELECT tc.constraint_name,
                       kcu.column_name AS source_column,
                       ccu.table_name AS target_table,
                       ccu.column_name AS target_column
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                  AND tc.table_schema = kcu.table_schema
                JOIN information_schema.constraint_column_usage ccu
                  ON ccu.constraint_name = tc.constraint_name
                  AND ccu.table_schema = tc.table_schema
                WHERE tc.constraint_type = 'FOREIGN KEY'
                  AND tc.table_schema = ? AND tc.table_name = ?
                """;
        List<ForeignKeyRelation> fks = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    fks.add(ForeignKeyRelation.builder()
                            .constraintName(rs.getString("constraint_name"))
                            .sourceTable(table)
                            .sourceColumn(rs.getString("source_column"))
                            .targetTable(rs.getString("target_table"))
                            .targetColumn(rs.getString("target_column"))
                            .build());
                }
            }
        }
        return fks;
    }

    private long getRowEstimate(Connection conn, String schema, String table) {
        String sql = "SELECT reltuples::bigint FROM pg_class c JOIN pg_namespace n ON c.relnamespace = n.oid WHERE n.nspname = ? AND c.relname = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Math.max(0, rs.getLong(1));
            }
        } catch (SQLException e) {
            log.debug("Could not get row estimate for {}.{}: {}", schema, table, e.getMessage());
        }
        return -1;
    }
}
