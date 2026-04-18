package com.dbpilot.mcp.tool;

import com.dbpilot.core.model.DatabaseConnectionInfo;
import com.dbpilot.core.model.TableMetadata;
import com.dbpilot.core.port.out.CredentialStore;
import com.dbpilot.core.port.out.DatabaseAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP Tool: Explore database schema.
 *
 * <p>Allows AI clients to list tables, describe columns, and inspect
 * relationships in the configured databases.</p>
 *
 * @author DB-Pilot
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaExplorerTool {

    private final DatabaseAdapter databaseAdapter;
    private final CredentialStore credentialStore;

    /**
     * Explores the database schema.
     *
     * @param databaseAlias the target database alias
     * @param command       what to explore: "tables", "columns:tableName", "relations:tableName"
     * @return formatted schema information
     */
    public String explore(String databaseAlias, String command) {
        log.info("[MCP] schemaExplore — db: '{}', command: '{}'", databaseAlias, command);

        try {
            DatabaseConnectionInfo connInfo = credentialStore.retrieve(databaseAlias);
            if (connInfo == null) {
                return "❌ No database configured with alias '%s'. Run db-pilot setup.".formatted(databaseAlias);
            }

            List<TableMetadata> tables = databaseAdapter.introspectSchema(connInfo);

            if (command == null || command.equalsIgnoreCase("tables")) {
                return formatTableList(tables, databaseAlias);
            } else if (command.toLowerCase().startsWith("columns:")) {
                String tableName = command.substring(8).trim();
                return formatColumns(tables, tableName);
            } else if (command.toLowerCase().startsWith("relations:")) {
                String tableName = command.substring(10).trim();
                return formatRelations(tables, tableName);
            } else {
                return "Unknown command. Use: 'tables', 'columns:TABLE_NAME', or 'relations:TABLE_NAME'";
            }

        } catch (Exception e) {
            log.error("[MCP] schemaExplore failed: {}", e.getMessage(), e);
            return "❌ Error: " + e.getMessage();
        }
    }

    private String formatTableList(List<TableMetadata> tables, String dbAlias) {
        var sb = new StringBuilder();
        sb.append("## Tables in '%s' (%d total)\n\n".formatted(dbAlias, tables.size()));
        sb.append("| Table | Columns | Est. Rows |\n");
        sb.append("| --- | --- | --- |\n");
        for (var t : tables) {
            sb.append("| %s | %d | %s |\n".formatted(
                    t.getTableName(),
                    t.getColumns() != null ? t.getColumns().size() : 0,
                    t.getEstimatedRowCount() >= 0 ? String.valueOf(t.getEstimatedRowCount()) : "?"));
        }
        return sb.toString();
    }

    private String formatColumns(List<TableMetadata> tables, String tableName) {
        var table = tables.stream()
                .filter(t -> t.getTableName().equalsIgnoreCase(tableName))
                .findFirst();

        if (table.isEmpty()) {
            return "❌ Table '%s' not found".formatted(tableName);
        }

        var t = table.get();
        var sb = new StringBuilder();
        sb.append("## Columns of '%s'\n\n".formatted(t.getTableName()));
        sb.append("| Column | Type | PK | Nullable |\n");
        sb.append("| --- | --- | --- | --- |\n");
        for (var col : t.getColumns()) {
            sb.append("| %s | %s | %s | %s |\n".formatted(
                    col.getName(), col.getDataType(),
                    col.isPrimaryKey() ? "✅" : "",
                    col.isNullable() ? "YES" : "NO"));
        }
        return sb.toString();
    }

    private String formatRelations(List<TableMetadata> tables, String tableName) {
        var table = tables.stream()
                .filter(t -> t.getTableName().equalsIgnoreCase(tableName))
                .findFirst();

        if (table.isEmpty()) {
            return "❌ Table '%s' not found".formatted(tableName);
        }

        var t = table.get();
        var sb = new StringBuilder();
        sb.append("## Foreign Keys of '%s'\n\n".formatted(t.getTableName()));

        if (t.getForeignKeys() == null || t.getForeignKeys().isEmpty()) {
            sb.append("No foreign keys found.\n");
        } else {
            sb.append("| Constraint | Column | → Table.Column |\n");
            sb.append("| --- | --- | --- |\n");
            for (var fk : t.getForeignKeys()) {
                sb.append("| %s | %s | %s.%s |\n".formatted(
                        fk.getConstraintName(), fk.getSourceColumn(),
                        fk.getTargetTable(), fk.getTargetColumn()));
            }
        }
        return sb.toString();
    }
}
