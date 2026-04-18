package com.dbpilot.core.model;

import lombok.Builder;
import lombok.Value;

/**
 * Value Object representing a table that has been ranked by the
 * triple-layer ranking algorithm.
 *
 * <p>Combines the original {@link TableMetadata} with a composite score
 * derived from semantic matching, relation expansion, and user frequency.</p>
 *
 * @author DB-Pilot
 */
@Value
@Builder
public class RankedTable {

    /** The underlying table metadata. */
    TableMetadata table;

    /** Composite ranking score (0.0 to 1.0). Higher is more relevant. */
    double score;

    /** Score from Layer 1: Semantic Match (vector/keyword similarity). */
    double semanticScore;

    /** Score from Layer 2: Relation Expansion (FK connectivity). */
    double relationScore;

    /** Score from Layer 3: Frequency Weighting (user query history). */
    double frequencyScore;

    /** Human-readable explanation of why this table was ranked highly. */
    String rankingReason;
}
