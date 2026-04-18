package com.dbpilot.core.model;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Value Object representing the result of a database query execution.
 *
 * <p>Encapsulates column names, row data, timing, and metadata.
 * Used as the return type from {@link com.dbpilot.core.port.out.DatabaseAdapter#executeQuery}.</p>
 *
 * @author DB-Pilot
 */
@Value
@Builder
public class QueryResult {

    /** The generated query that was executed (SQL or aggregation pipeline). */
    String query;

    /** Column/field names in order. */
    List<String> columnNames;

    /** Rows of data — each row is a map of column name to value. */
    List<Map<String, Object>> rows;

    /** Total number of rows returned. */
    int rowCount;

    /** Total number of rows affected (for DML operations). */
    int affectedRows;

    /** Query execution duration. */
    Duration executionTime;

    /** Whether this was a dry-run (EXPLAIN) execution. */
    boolean dryRun;

    /** Explain plan output (populated in dry-run mode). */
    String explainPlan;

    /** Warning messages from the database, if any. */
    List<String> warnings;

    /** Error message, if the query failed. */
    String errorMessage;

    /**
     * Returns {@code true} if the query executed successfully.
     *
     * @return success status
     */
    public boolean isSuccess() {
        return errorMessage == null || errorMessage.isBlank();
    }

    /**
     * Returns a human-readable summary of the result.
     *
     * @return summary string
     */
    public String toSummary() {
        if (!isSuccess()) {
            return "❌ Error: " + errorMessage;
        }
        if (dryRun) {
            return "✅ Dry-run OK — EXPLAIN plan generated (%s)".formatted(executionTime);
        }
        return "✅ %d row(s) returned in %s".formatted(rowCount, executionTime);
    }
}
