package com.dbpilot.core.model;

import lombok.Builder;
import lombok.Value;

/**
 * Value Object representing a single column within a database table.
 *
 * <p>Used during schema introspection and for building compact YAML metadata
 * that is sent to the LLM as context.</p>
 *
 * @author DB-Pilot
 */
@Value
@Builder
public class ColumnMetadata {

    /** Column name. */
    String name;

    /** Data type (e.g., VARCHAR2(100), INTEGER, TIMESTAMP). */
    String dataType;

    /** Whether this column is nullable. */
    boolean nullable;

    /** Whether this column is part of the primary key. */
    boolean primaryKey;

    /** Constraint description (e.g., "UNIQUE", "NOT NULL", "CHECK(age > 0)"). */
    String constraint;

    /** Optional human-readable description or comment. */
    String description;

    /** Default value expression, if any. */
    String defaultValue;

    /**
     * Returns a compact representation for token-efficient LLM context.
     * Format: {@code name:type:constraint}
     *
     * @return compact string like "user_id:INT:PK" or "email:VARCHAR:UQ,NN"
     */
    public String toCompactString() {
        var sb = new StringBuilder();
        sb.append(name).append(':').append(simplifyType(dataType));

        var constraints = new StringBuilder();
        if (primaryKey) constraints.append("PK");
        if (!nullable) {
            if (!constraints.isEmpty()) constraints.append(',');
            constraints.append("NN");
        }
        if (constraint != null && !constraint.isBlank()) {
            if (!constraints.isEmpty()) constraints.append(',');
            constraints.append(constraint);
        }

        if (!constraints.isEmpty()) {
            sb.append(':').append(constraints);
        }
        return sb.toString();
    }

    /**
     * Simplifies verbose SQL types for token efficiency.
     * E.g., "CHARACTER VARYING(255)" → "VARCHAR", "BIGINT" → "BIGINT"
     */
    private String simplifyType(String type) {
        if (type == null) return "UNKNOWN";
        String upper = type.toUpperCase().trim();
        // Strip length/precision specifiers for compactness
        int parenIdx = upper.indexOf('(');
        return parenIdx > 0 ? upper.substring(0, parenIdx) : upper;
    }
}
