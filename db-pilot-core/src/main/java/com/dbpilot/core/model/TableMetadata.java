package com.dbpilot.core.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Entity representing a database table with its columns and relationships.
 *
 * <p>Central metadata object used throughout the system — from schema introspection
 * to prompt assembly. Designed to be serializable into ultra-compact YAML format
 * for token-efficient LLM context.</p>
 *
 * @author DB-Pilot
 */
@Value
@Builder
public class TableMetadata {

    /** Fully-qualified table name (schema.table or just table). */
    String tableName;

    /** Schema/owner name (e.g., "public", "dbo", "HR"). */
    String schemaName;

    /** Optional table comment/description from database catalog. */
    String description;

    /** Estimated row count (from statistics, not COUNT(*)). */
    long estimatedRowCount;

    /** All columns in this table. */
    List<ColumnMetadata> columns;

    /** Foreign keys originating FROM this table. */
    List<ForeignKeyRelation> foreignKeys;

    /** Foreign keys pointing TO this table (reverse references). */
    List<ForeignKeyRelation> referencedBy;

    /**
     * Returns a compact YAML representation optimized for LLM token efficiency.
     *
     * <p>Format:</p>
     * <pre>
     * t:[tableName]; c:[col1:type:constraint, col2:type]; r:[FK1, FK2]
     * </pre>
     *
     * @return ultra-compact YAML string
     */
    public String toCompactYaml() {
        var sb = new StringBuilder();
        sb.append("t:").append(tableName);

        // Columns
        if (columns != null && !columns.isEmpty()) {
            sb.append("; c:[");
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(columns.get(i).toCompactString());
            }
            sb.append(']');
        }

        // Foreign keys
        if (foreignKeys != null && !foreignKeys.isEmpty()) {
            sb.append("; r:[");
            for (int i = 0; i < foreignKeys.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(foreignKeys.get(i).toCompactString());
            }
            sb.append(']');
        }

        return sb.toString();
    }

    /**
     * Returns the qualified table name (schema.table) if schema is present.
     *
     * @return qualified name
     */
    public String getQualifiedName() {
        if (schemaName != null && !schemaName.isBlank()) {
            return schemaName + "." + tableName;
        }
        return tableName;
    }
}
