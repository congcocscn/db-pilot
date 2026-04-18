package com.dbpilot.core.service;

import com.dbpilot.core.model.ColumnMetadata;
import com.dbpilot.core.model.CompactSchema;
import com.dbpilot.core.model.DatabaseType;
import com.dbpilot.core.model.RankedTable;
import com.dbpilot.core.model.TableMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Domain service that converts full {@link TableMetadata} into ultra-compact
 * YAML format for token-efficient LLM context.
 *
 * <p>The compaction process:</p>
 * <ol>
 *   <li>Strips non-essential columns (audit logs, internal IDs, timestamps matching patterns)</li>
 *   <li>Normalizes to compact format: {@code t:[name]; c:[name:type:constraint]; r:[FK]}</li>
 *   <li>Estimates the resulting token count</li>
 * </ol>
 *
 * @author DB-Pilot
 */
public class SchemaCompactor {

    /** Column name patterns to strip (audit columns, internal metadata). */
    private static final Set<String> STRIP_PATTERNS = Set.of(
            "created_at", "updated_at", "created_by", "updated_by",
            "modified_at", "modified_by", "created_date", "modified_date",
            "last_modified", "insert_timestamp", "update_timestamp",
            "row_version", "version", "audit_id", "sys_", "_audit",
            "is_deleted", "deleted_at", "deleted_by"
    );

    /**
     * Compacts a list of ranked tables into token-efficient YAML.
     *
     * @param rankedTables   tables sorted by ranking score (descending)
     * @param databaseAlias  the database alias
     * @param databaseType   the database engine type
     * @return compact schema ready for prompt injection
     */
    public CompactSchema compact(List<RankedTable> rankedTables,
                                  String databaseAlias,
                                  DatabaseType databaseType) {

        List<String> entries = new ArrayList<>();
        int totalChars = 0;

        for (RankedTable ranked : rankedTables) {
            TableMetadata table = ranked.getTable();
            TableMetadata stripped = stripNonEssentialColumns(table);
            String compactEntry = stripped.toCompactYaml();
            entries.add(compactEntry);
            totalChars += compactEntry.length();
        }

        // Estimate tokens (≈4 chars per token)
        int estimatedTokens = (int) Math.ceil(totalChars / 4.0);

        return CompactSchema.builder()
                .databaseAlias(databaseAlias)
                .databaseType(databaseType)
                .compactTableEntries(entries)
                .estimatedTokenCount(estimatedTokens)
                .build();
    }

    /**
     * Strips non-essential columns from a table based on naming patterns.
     *
     * @param table the original table metadata
     * @return a new TableMetadata with audit/internal columns removed
     */
    private TableMetadata stripNonEssentialColumns(TableMetadata table) {
        List<ColumnMetadata> filtered = table.getColumns().stream()
                .filter(col -> !shouldStrip(col.getName()))
                .toList();

        return TableMetadata.builder()
                .tableName(table.getTableName())
                .schemaName(table.getSchemaName())
                .description(table.getDescription())
                .estimatedRowCount(table.getEstimatedRowCount())
                .columns(filtered)
                .foreignKeys(table.getForeignKeys())
                .referencedBy(table.getReferencedBy())
                .build();
    }

    /**
     * Determines whether a column should be stripped based on its name.
     */
    private boolean shouldStrip(String columnName) {
        if (columnName == null) return false;
        String lower = columnName.toLowerCase().trim();
        return STRIP_PATTERNS.stream().anyMatch(pattern ->
                lower.equals(pattern) || lower.contains(pattern));
    }
}
