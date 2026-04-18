package com.dbpilot.infrastructure.adapter.db;

import com.dbpilot.core.model.*;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link com.dbpilot.core.port.out.DatabaseAdapter} implementation for MySQL.
 *
 * @author DB-Pilot
 */
@Slf4j
public class MySqlDatabaseAdapter extends AbstractJdbcDatabaseAdapter {

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.MYSQL;
    }

    @Override
    public List<TableMetadata> introspectSchema(DatabaseConnectionInfo connectionInfo) {
        String database = connectionInfo.getDatabaseName();
        List<TableMetadata> tables = new ArrayList<>();

        try (Connection conn = getConnection(connectionInfo)) {
            List<String> tableNames = getTableNames(conn, database);

            for (String tableName : tableNames) {
                List<ColumnMetadata> columns = getColumns(conn, database, tableName);
                List<ForeignKeyRelation> foreignKeys = getForeignKeys(conn, database, tableName);
                long rowEstimate = getRowEstimate(conn, database, tableName);

                tables.add(TableMetadata.builder()
                        .tableName(tableName)
                        .schemaName(database)
                        .columns(columns)
                        .foreignKeys(foreignKeys)
                        .referencedBy(List.of())
                        .estimatedRowCount(rowEstimate)
                        .build());
            }

            log.info("MySQL introspection for '{}': found {} tables", connectionInfo.getAlias(), tables.size());

        } catch (SQLException e) {
            throw new RuntimeException("MySQL schema introspection failed: " + mapError(e), e);
        }
        return tables;
    }

    @Override
    protected String buildExplainQuery(String query) {
        return "EXPLAIN FORMAT=TRADITIONAL " + query;
    }

    @Override
    protected String mapError(SQLException e) {
        int code = e.getErrorCode();
        String hint = switch (code) {
            case 1146 -> "Table doesn't exist. Verify database and table name.";
            case 1054 -> "Unknown column. Check column name spelling.";
            case 1064 -> "SQL syntax error. Check query syntax.";
            case 1045 -> "Access denied. Check username and password.";
            case 2003 -> "Cannot connect to MySQL. Check if the server is running.";
            case 1062 -> "Duplicate entry for unique key.";
            case 1452 -> "Foreign key constraint fails.";
            default -> e.getMessage();
        };
        return "[MySQL] %s (Error: %d)".formatted(hint, code);
    }

    private List<String> getTableNames(Connection conn, String database) throws SQLException {
        String sql = """
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = ? AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """;
        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, database);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString("TABLE_NAME"));
            }
        }
        return names;
    }

    private List<ColumnMetadata> getColumns(Connection conn, String database, String table) throws SQLException {
        String sql = """
                SELECT column_name, column_type, is_nullable, column_key, column_default, extra
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ?
                ORDER BY ordinal_position
                """;

        List<ColumnMetadata> columns = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, database);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(ColumnMetadata.builder()
                            .name(rs.getString("COLUMN_NAME"))
                            .dataType(rs.getString("COLUMN_TYPE"))
                            .nullable("YES".equals(rs.getString("IS_NULLABLE")))
                            .primaryKey("PRI".equals(rs.getString("COLUMN_KEY")))
                            .defaultValue(rs.getString("COLUMN_DEFAULT"))
                            .build());
                }
            }
        }
        return columns;
    }

    private List<ForeignKeyRelation> getForeignKeys(Connection conn, String database, String table) throws SQLException {
        String sql = """
                SELECT constraint_name, column_name, referenced_table_name, referenced_column_name
                FROM information_schema.key_column_usage
                WHERE table_schema = ? AND table_name = ? AND referenced_table_name IS NOT NULL
                """;
        List<ForeignKeyRelation> fks = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, database);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    fks.add(ForeignKeyRelation.builder()
                            .constraintName(rs.getString("CONSTRAINT_NAME"))
                            .sourceTable(table)
                            .sourceColumn(rs.getString("COLUMN_NAME"))
                            .targetTable(rs.getString("REFERENCED_TABLE_NAME"))
                            .targetColumn(rs.getString("REFERENCED_COLUMN_NAME"))
                            .build());
                }
            }
        }
        return fks;
    }

    private long getRowEstimate(Connection conn, String database, String table) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT table_rows FROM information_schema.tables WHERE table_schema = ? AND table_name = ?")) {
            ps.setString(1, database);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Math.max(0, rs.getLong(1));
            }
        } catch (SQLException e) {
            log.debug("Could not get row estimate for {}.{}", database, table);
        }
        return -1;
    }
}
