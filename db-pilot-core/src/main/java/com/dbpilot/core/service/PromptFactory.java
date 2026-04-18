package com.dbpilot.core.service;

import com.dbpilot.core.model.BusinessRule;
import com.dbpilot.core.model.CompactSchema;
import com.dbpilot.core.model.DatabaseType;
import com.dbpilot.core.model.RankedTable;
import com.dbpilot.core.model.UserHabit;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Domain service responsible for assembling the final LLM prompt.
 *
 * <p>Combines four layers of context:</p>
 * <ol>
 *   <li><strong>Ranked Schema:</strong> YAML-compact table metadata, ordered by relevance</li>
 *   <li><strong>Global Rules:</strong> Company-wide business rules</li>
 *   <li><strong>User Habits:</strong> Personal preferences and correction history</li>
 *   <li><strong>User Intent:</strong> The natural language query (passed separately)</li>
 * </ol>
 *
 * <p>The assembled prompt is then passed through {@link TokenGuard} to enforce
 * token budget limits before being sent to the LLM.</p>
 *
 * @author DB-Pilot
 */
@RequiredArgsConstructor
public class PromptFactory {

    private final TokenGuard tokenGuard;

    /**
     * Assembles the system prompt from ranked schema, global rules, and user habits.
     *
     * @param databaseType   the target database engine
     * @param compactSchema  the ranked and compacted schema
     * @param globalRules    company-wide business rules
     * @param userRules      user-specific business rules
     * @param userHabits     user query habits for personalization
     * @return the assembled system prompt (ready to send to LLM)
     */
    public String assembleSystemPrompt(DatabaseType databaseType,
                                        CompactSchema compactSchema,
                                        List<BusinessRule> globalRules,
                                        List<BusinessRule> userRules,
                                        List<UserHabit> userHabits) {

        var sb = new StringBuilder();

        // Header
        sb.append("You are DB-Pilot, an expert database query generator for %s.\n".formatted(
                databaseType.getDisplayName()));
        sb.append("Generate ONLY the query. No explanations, no markdown, no comments.\n\n");

        // Database-specific instructions
        sb.append(getDatabaseInstructions(databaseType));
        sb.append('\n');

        // Ranked Schema (YAML compact)
        sb.append("## Schema\n");
        sb.append(compactSchema.toYamlBlock());
        sb.append("\n\n");

        // Global Rules
        if (globalRules != null && !globalRules.isEmpty()) {
            sb.append("## Global Rules\n");
            for (var rule : globalRules) {
                sb.append("- ").append(rule.getRuleText()).append('\n');
            }
            sb.append('\n');
        }

        // User Rules
        if (userRules != null && !userRules.isEmpty()) {
            sb.append("## User Preferences\n");
            for (var rule : userRules) {
                sb.append("- ").append(rule.getRuleText()).append('\n');
            }
            sb.append('\n');
        }

        // User Habits (frequently used tables)
        if (userHabits != null && !userHabits.isEmpty()) {
            var topTables = userHabits.stream()
                    .sorted((a, b) -> Integer.compare(b.getQueryCount(), a.getQueryCount()))
                    .limit(5)
                    .map(h -> "%s (used %d times)".formatted(h.getTableName(), h.getQueryCount()))
                    .collect(Collectors.joining(", "));
            sb.append("## Frequently Used Tables\n");
            sb.append(topTables).append("\n\n");
        }

        // Enforce token budget
        String rawPrompt = sb.toString();
        return tokenGuard.enforce(rawPrompt);
    }

    /**
     * Returns database-specific query generation instructions.
     */
    private String getDatabaseInstructions(DatabaseType type) {
        return switch (type) {
            case ORACLE -> """
                    ## Instructions (Oracle)
                    - Use Oracle SQL syntax (e.g., ROWNUM, NVL, DUAL, TO_DATE).
                    - Use double quotes for case-sensitive identifiers.
                    - Limit results with FETCH FIRST N ROWS ONLY (12c+) or ROWNUM.
                    """;
            case POSTGRESQL -> """
                    ## Instructions (PostgreSQL)
                    - Use PostgreSQL syntax (e.g., LIMIT, OFFSET, COALESCE, ::type casting).
                    - Use double quotes for case-sensitive identifiers.
                    - Leverage CTEs (WITH) for complex queries.
                    """;
            case MYSQL -> """
                    ## Instructions (MySQL)
                    - Use MySQL syntax (e.g., LIMIT, IFNULL, backtick identifiers).
                    - Use backticks (`) for identifier quoting.
                    """;
            case MARIADB -> """
                    ## Instructions (MariaDB)
                    - Use MariaDB syntax (compatible with MySQL, supports CTEs and window functions).
                    - Use backticks (`) for identifier quoting.
                    """;
            case MONGODB -> """
                    ## Instructions (MongoDB)
                    - Generate a MongoDB aggregation pipeline as a JSON array.
                    - Use $match, $group, $project, $sort, $limit, $lookup for joins.
                    - Output ONLY the JSON array — no db.collection.aggregate() wrapper.
                    - Example: [{"$match": {"status": "active"}}, {"$group": {"_id": "$category", "count": {"$sum": 1}}}]
                    """;
        };
    }
}
