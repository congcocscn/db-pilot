package com.dbpilot.infrastructure.adapter.db;

import com.dbpilot.core.model.*;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link com.dbpilot.core.port.out.DatabaseAdapter} implementation for Oracle Database.
 *
 * <p>Schema introspection uses Oracle's data dictionary views
 * (ALL_TAB_COLUMNS, ALL_CONSTRAINTS, ALL_CONS_COLUMNS).</p>
 *
 * @author DB-Pilot
 */
@Slf4j
public class OracleDatabaseAdapter extends AbstractJdbcDatabaseAdapter {

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.ORACLE;
    }

    @Override
    public List<TableMetadata> introspectSchema(DatabaseConnectionInfo connectionInfo) {
        String owner = connectionInfo.getSchema() != null
                ? connectionInfo.getSchema().toUpperCase()
                : connectionInfo.getUsername().toUpperCase();

        List<TableMetadata> tables = new ArrayList<>();

        try (Connection conn = getConnection(connectionInfo)) {
            List<String> tableNames = getTableNames(conn, owner);

            for (String tableName : tableNames) {
                List<ColumnMetadata> columns = getColumns(conn, owner, tableName);
                List<ForeignKeyRelation> foreignKeys = getForeignKeys(conn, owner, tableName);
                long rowEstimate = getRowEstimate(conn, owner, tableName);

                tables.add(TableMetadata.builder()
                        .tableName(tableName)
                        .schemaName(owner)
                        .columns(columns)
                        .foreignKeys(foreignKeys)
                        .referencedBy(List.of())
                        .estimatedRowCount(rowEstimate)
                        .build());
            }

            log.info("Oracle introspection for '{}': found {} tables for owner '{}'",
                    connectionInfo.getAlias(), tables.size(), owner);

        } catch (SQLException e) {
            throw new RuntimeException("Oracle schema introspection failed: " + mapError(e), e);
        }
        return tables;
    }

    @Override
    protected String buildExplainQuery(String query) {
        // Oracle requires EXPLAIN PLAN then reading from plan table
        // For simplicity, we use a workaround with EXPLAIN PLAN INTO
        return "EXPLAIN PLAN FOR " + query;
    }

    @Override
    public QueryResult explainQuery(DatabaseConnectionInfo connectionInfo, String query) {
        var start = java.time.Instant.now();
        try (Connection conn = getConnection(connectionInfo);
             Statement stmt = conn.createStatement()) {

            // Execute EXPLAIN PLAN
            stmt.execute("EXPLAIN PLAN FOR " + query);

            // Read the plan
            StringBuilder plan = new StringBuilder();
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY())")) {
                while (rs.next()) {
                    plan.append(rs.getString(1)).append('\n');
                }
            }

            var elapsed = java.time.Duration.between(start, java.time.Instant.now());
            return QueryResult.builder()
                    .query(query)
                    .columnNames(List.of())
                    .rows(List.of())
                    .executionTime(elapsed)
                    .dryRun(true)
                    .explainPlan(plan.toString())
                    .build();

        } catch (SQLException e) {
            var elapsed = java.time.Duration.between(start, java.time.Instant.now());
            return QueryResult.builder()
                    .query(query)
                    .executionTime(elapsed)
                    .dryRun(true)
                    .errorMessage("EXPLAIN failed: " + mapError(e))
                    .build();
        }
    }

    @Override
    protected String mapError(SQLException e) {
        int errorCode = e.getErrorCode();
        String hint = switch (errorCode) {
            case 942 -> "Table or view does not exist. Check table name and schema access.";
            case 904 -> "Invalid column identifier. Verify column name spelling.";
            case 900 -> "Invalid SQL statement. Check syntax.";
            case 1017 -> "Invalid username/password. Check credentials.";
            case 12541 -> "TNS: no listener. Check if Oracle is running and listener is started.";
            case 12154 -> "TNS: could not resolve the connect identifier. Check SID/service name.";
            case 1 -> "Unique constraint violated. Duplicate key value.";
            case 2291 -> "Foreign key constraint violated. Parent key not found.";
            case 936 -> "Missing expression in SQL statement.";
            default -> e.getMessage();
        };
        return "[Oracle] %s (ORA-%05d)".formatted(hint, errorCode);
    }

    private List<String> getTableNames(Connection conn, String owner) throws SQLException {
        String sql = "SELECT table_name FROM all_tables WHERE owner = ? ORDER BY table_name";
        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString("TABLE_NAME"));
            }
        }
        return names;
    }

    private List<ColumnMetadata> getColumns(Connection conn, String owner, String table) throws SQLException {
        String sql = """
                SELECT c.column_name, c.data_type, c.data_length, c.nullable, c.data_default,
                       CASE WHEN pk.column_name IS NOT NULL THEN 1 ELSE 0 END AS is_pk
                FROM all_tab_columns c
                LEFT JOIN (
                    SELECT cols.column_name
                    FROM all_constraints cons
                    JOIN all_cons_columns cols ON cons.constraint_name = cols.constraint_name AND cons.owner = cols.owner
                    WHERE cons.constraint_type = 'P' AND cons.owner = ? AND cons.table_name = ?
                ) pk ON c.column_name = pk.column_name
                WHERE c.owner = ? AND c.table_name = ?
                ORDER BY c.column_id
                """;

        List<ColumnMetadata> columns = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, owner);
            ps.setString(2, table);
            ps.setString(3, owner);
            ps.setString(4, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String dataType = rs.getString("DATA_TYPE");
                    int dataLen = rs.getInt("DATA_LENGTH");
                    String fullType = dataLen > 0 ? "%s(%d)".formatted(dataType, dataLen) : dataType;

                    columns.add(ColumnMetadata.builder()
                            .name(rs.getString("COLUMN_NAME"))
                            .dataType(fullType)
                            .nullable("Y".equals(rs.getString("NULLABLE")))
                            .primaryKey(rs.getInt("IS_PK") == 1)
                            .defaultValue(rs.getString("DATA_DEFAULT"))
                            .build());
                }
            }
        }
        return columns;
    }

    private List<ForeignKeyRelation> getForeignKeys(Connection conn, String owner, String table) throws SQLException {
        String sql = """
                SELECT a.constraint_name, a.column_name AS source_column,
                       c_pk.table_name AS target_table, b.column_name AS target_column
                FROM all_cons_columns a
                JOIN all_constraints c ON a.constraint_name = c.constraint_name AND a.owner = c.owner
                JOIN all_constraints c_pk ON c.r_constraint_name = c_pk.constraint_name AND c.r_owner = c_pk.owner
                JOIN all_cons_columns b ON c_pk.constraint_name = b.constraint_name AND c_pk.owner = b.owner AND a.position = b.position
                WHERE c.constraint_type = 'R' AND a.owner = ? AND a.table_name = ?
                """;

        List<ForeignKeyRelation> fks = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, owner);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    fks.add(ForeignKeyRelation.builder()
                            .constraintName(rs.getString("CONSTRAINT_NAME"))
                            .sourceTable(table)
                            .sourceColumn(rs.getString("SOURCE_COLUMN"))
                            .targetTable(rs.getString("TARGET_TABLE"))
                            .targetColumn(rs.getString("TARGET_COLUMN"))
                            .build());
                }
            }
        }
        return fks;
    }

    private long getRowEstimate(Connection conn, String owner, String table) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT num_rows FROM all_tables WHERE owner = ? AND table_name = ?")) {
            ps.setString(1, owner);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Math.max(0, rs.getLong(1));
            }
        } catch (SQLException e) {
            log.debug("Could not get row estimate for {}.{}", owner, table);
        }
        return -1;
    }
}
