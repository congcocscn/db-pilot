package com.dbpilot.core.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Entity representing a learned business rule extracted from user corrections.
 *
 * <p>When a user corrects a generated query, the system triggers an LLM chain
 * to extract a generic, reusable business rule. Rules are scoped to either
 * {@code GLOBAL} (shared across all users) or {@code USER} (personal to a specific userId).</p>
 *
 * <p>Example rules:</p>
 * <ul>
 *   <li>"Exclude records where status = 'DELETED' unless explicitly requested."</li>
 *   <li>"When querying orders, always join with customer table for context."</li>
 *   <li>"Default date range is last 30 days for time-series queries."</li>
 * </ul>
 *
 * @author DB-Pilot
 */
@Value
@Builder
public class BusinessRule {

    /** Unique identifier. */
    String id;

    /** The rule text in natural language. */
    String ruleText;

    /** Scope of this rule. */
    Scope scope;

    /** User who triggered the learning (null for GLOBAL scope). */
    String userId;

    /** Database alias this rule applies to (null for cross-database rules). */
    String databaseAlias;

    /** When this rule was learned. */
    Instant createdAt;

    /** Number of times this rule has been applied. */
    int applicationCount;

    /** Confidence score from the LLM extraction (0.0 to 1.0). */
    double confidence;

    /**
     * Scope enumeration for business rules.
     */
    public enum Scope {
        /** Shared across all users — company-wide rules. */
        GLOBAL,
        /** Private to a specific user — personal preferences. */
        USER
    }
}
