package com.dbpilot.core.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Entity representing a user's query habit/pattern.
 *
 * <p>Tracks which tables a specific user queries most frequently, enabling
 * the frequency weighting layer (Layer 3) of the triple-layer table ranking.
 * Also stores common query patterns for personalized prompt hints.</p>
 *
 * @author DB-Pilot
 */
@Value
@Builder
public class UserHabit {

    /** Unique identifier. */
    String id;

    /** The user this habit belongs to. */
    String userId;

    /** Database alias this habit is associated with. */
    String databaseAlias;

    /** Table name that was queried. */
    String tableName;

    /** Number of times this table has been queried by this user. */
    int queryCount;

    /** Common query pattern or natural language pattern (optional). */
    String queryPattern;

    /** Last time this habit was updated. */
    Instant lastUsedAt;

    /** First time this habit was recorded. */
    Instant firstUsedAt;
}
