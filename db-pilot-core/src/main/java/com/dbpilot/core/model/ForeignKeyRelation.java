package com.dbpilot.core.model;

import lombok.Builder;
import lombok.Value;

/**
 * Value Object representing a foreign key relationship between two tables.
 *
 * <p>Used by the {@link com.dbpilot.core.port.out.TableRanker} to expand
 * the set of relevant tables via relation traversal (Layer 2 of triple-layer ranking).</p>
 *
 * @author DB-Pilot
 */
@Value
@Builder
public class ForeignKeyRelation {

    /** Name of the FK constraint. */
    String constraintName;

    /** Source table containing the FK column. */
    String sourceTable;

    /** Source column holding the FK reference. */
    String sourceColumn;

    /** Target (referenced) table. */
    String targetTable;

    /** Target (referenced) column (typically the PK). */
    String targetColumn;

    /**
     * Returns a compact representation for token-efficient LLM context.
     * Format: {@code sourceTable.sourceColumn->targetTable.targetColumn}
     *
     * @return compact FK notation
     */
    public String toCompactString() {
        return "%s.%s->%s.%s".formatted(sourceTable, sourceColumn, targetTable, targetColumn);
    }
}
