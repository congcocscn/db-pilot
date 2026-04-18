package com.dbpilot.core.port.in;

import com.dbpilot.core.model.BusinessRule;

/**
 * Inbound port for the learning loop: when a user corrects a query,
 * extract a reusable business rule and persist it.
 *
 * @author DB-Pilot
 */
public interface LearnFromCorrectionUseCase {

    /**
     * Command for submitting a query correction.
     *
     * @param userId         the user making the correction
     * @param databaseAlias  the target database
     * @param originalQuery  the query that was originally generated
     * @param correctedQuery the user's corrected version
     * @param explanation    optional user explanation of what was wrong
     */
    record CorrectionCommand(
            String userId,
            String databaseAlias,
            String originalQuery,
            String correctedQuery,
            String explanation
    ) {}

    /**
     * Processes a user correction and extracts a business rule.
     *
     * <p>Pipeline:</p>
     * <ol>
     *   <li>Send original + corrected query to LLM for rule extraction</li>
     *   <li>Classify rule scope (GLOBAL vs USER)</li>
     *   <li>Persist rule to KnowledgeStore</li>
     *   <li>Return the extracted rule for user confirmation</li>
     * </ol>
     *
     * @param command the correction command
     * @return the extracted and saved business rule
     */
    BusinessRule learn(CorrectionCommand command);
}
