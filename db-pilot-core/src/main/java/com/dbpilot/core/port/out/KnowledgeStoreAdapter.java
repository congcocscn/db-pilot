package com.dbpilot.core.port.out;

import com.dbpilot.core.model.BusinessRule;
import com.dbpilot.core.model.TableMetadata;
import com.dbpilot.core.model.UserHabit;

import java.util.List;

/**
 * Outbound port for storing and retrieving learned knowledge.
 *
 * <p>Supports multi-tenant habit isolation:</p>
 * <ul>
 *   <li><strong>Global Scope:</strong> Shared schema metadata and company-wide business rules.</li>
 *   <li><strong>User Scope:</strong> Private habits, hints, and historical corrections by {@code userId}.</li>
 * </ul>
 *
 * <p>Default implementation uses an embedded H2 database for local-first speed.
 * Extensible to VectorDB (pgvector/Milvus) via the same interface.</p>
 *
 * @author DB-Pilot
 */
public interface KnowledgeStoreAdapter {

    // ==================== Global Scope ====================

    /**
     * Saves a global business rule (shared across all users).
     *
     * @param rule the business rule to save
     */
    void saveGlobalRule(BusinessRule rule);

    /**
     * Retrieves all global business rules.
     *
     * @return list of global rules
     */
    List<BusinessRule> getGlobalRules();

    /**
     * Retrieves global rules relevant to a specific database.
     *
     * @param databaseAlias the database alias to filter by
     * @return list of relevant global rules
     */
    List<BusinessRule> getGlobalRules(String databaseAlias);

    /**
     * Caches the introspected schema metadata for a database.
     *
     * @param databaseAlias unique alias for the database connection
     * @param tables        the schema metadata to cache
     */
    void cacheSchema(String databaseAlias, List<TableMetadata> tables);

    /**
     * Retrieves cached schema metadata for a database.
     *
     * @param databaseAlias the database alias
     * @return cached table metadata, or empty list if no cache exists
     */
    List<TableMetadata> getCachedSchema(String databaseAlias);

    /**
     * Checks whether cached schema exists and is fresh.
     *
     * @param databaseAlias the database alias
     * @return {@code true} if a fresh cache exists
     */
    boolean isSchemaCached(String databaseAlias);

    // ==================== User Scope ====================

    /**
     * Saves or updates a user habit (table query frequency).
     *
     * @param userId the user identifier
     * @param habit  the habit to record
     */
    void saveUserHabit(String userId, UserHabit habit);

    /**
     * Retrieves all habits for a specific user.
     *
     * @param userId the user identifier
     * @return list of user habits
     */
    List<UserHabit> getUserHabits(String userId);

    /**
     * Retrieves habits for a specific user on a specific database.
     *
     * @param userId        the user identifier
     * @param databaseAlias the database alias
     * @return list of filtered user habits
     */
    List<UserHabit> getUserHabits(String userId, String databaseAlias);

    /**
     * Records a table access for frequency tracking.
     *
     * @param userId        the user identifier
     * @param databaseAlias the database alias
     * @param tableName     the table that was queried
     */
    void recordTableAccess(String userId, String databaseAlias, String tableName);

    /**
     * Saves a user-scoped business rule (personal preferences).
     *
     * @param userId the user identifier
     * @param rule   the business rule to save
     */
    void saveUserRule(String userId, BusinessRule rule);

    /**
     * Retrieves all business rules for a specific user.
     *
     * @param userId the user identifier
     * @return list of user-scoped rules
     */
    List<BusinessRule> getUserRules(String userId);

    // ==================== Maintenance ====================

    /**
     * Verifies the integrity of the knowledge store.
     *
     * @return a human-readable health status message
     */
    String healthCheck();
}
