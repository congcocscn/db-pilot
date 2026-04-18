package com.dbpilot.core.service;

import com.dbpilot.core.model.BusinessRule;
import com.dbpilot.core.port.out.KnowledgeStoreAdapter;
import com.dbpilot.core.port.out.LlmGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Domain service implementing the Atomic Learning Loop.
 *
 * <p>When a user corrects a generated query, this service:</p>
 * <ol>
 *   <li>Sends the original + corrected query to the LLM</li>
 *   <li>Asks the LLM to extract a generic, reusable business rule</li>
 *   <li>Classifies the rule scope (GLOBAL vs USER)</li>
 *   <li>Persists the rule to the appropriate scope in the Knowledge Store</li>
 * </ol>
 *
 * <p>Example: if a user corrects "SELECT * FROM orders" to
 * "SELECT * FROM orders WHERE status != 'DELETED'", the LLM might extract
 * the rule: "Exclude records with status='DELETED' unless explicitly requested."</p>
 *
 * @author DB-Pilot
 */
@Slf4j
@RequiredArgsConstructor
public class LearningLoopService {

    private final LlmGateway llmGateway;
    private final KnowledgeStoreAdapter knowledgeStore;

    /**
     * Processes a user correction and extracts a business rule.
     *
     * @param userId         the user making the correction
     * @param databaseAlias  the target database
     * @param originalQuery  the query that was originally generated
     * @param correctedQuery the user's corrected version
     * @param schemaContext  relevant schema context for the LLM
     * @return the extracted and persisted business rule
     */
    public BusinessRule learnFromCorrection(String userId,
                                             String databaseAlias,
                                             String originalQuery,
                                             String correctedQuery,
                                             String schemaContext) {

        log.info("Learning from correction by user '{}' on database '{}'", userId, databaseAlias);
        log.debug("Original: {}", originalQuery);
        log.debug("Corrected: {}", correctedQuery);

        // Ask the LLM to extract the business rule
        BusinessRule extractedRule = llmGateway.extractBusinessRule(
                originalQuery, correctedQuery, schemaContext);

        if (extractedRule == null) {
            log.warn("LLM could not extract a business rule from the correction");
            return null;
        }

        // Persist based on scope
        if (extractedRule.getScope() == BusinessRule.Scope.GLOBAL) {
            log.info("Saving GLOBAL rule: {}", extractedRule.getRuleText());
            knowledgeStore.saveGlobalRule(extractedRule);
        } else {
            log.info("Saving USER rule for '{}': {}", userId, extractedRule.getRuleText());
            knowledgeStore.saveUserRule(userId, extractedRule);
        }

        return extractedRule;
    }
}
