package com.dbpilot.core.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Value Object representing a compacted schema in ultra-compact YAML format.
 *
 * <p>Designed for maximum token efficiency when injecting schema context into
 * the LLM prompt. The format strips all non-essential metadata (audit columns,
 * internal IDs) and uses the notation:</p>
 *
 * <pre>
 * t:[tableName]; c:[col:type:constraint, ...]; r:[FK, ...]
 * </pre>
 *
 * @author DB-Pilot
 */
@Value
@Builder
public class CompactSchema {

    /** Database alias this schema belongs to. */
    String databaseAlias;

    /** Database type for query syntax guidance. */
    DatabaseType databaseType;

    /** List of tables in compact YAML notation (one string per table). */
    List<String> compactTableEntries;

    /** Estimated token count for this schema representation. */
    int estimatedTokenCount;

    /**
     * Assembles the full compact schema as a single string.
     *
     * @return newline-separated compact YAML entries for all tables
     */
    public String toYamlBlock() {
        return String.join("\n", compactTableEntries);
    }
}
