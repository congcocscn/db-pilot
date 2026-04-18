package com.dbpilot.core.port.out;

import com.dbpilot.core.model.BusinessRule;

/**
 * Outbound port for LLM interactions.
 *
 * <p>Provides a provider-agnostic interface for all LLM operations.
 * Default implementation uses Anthropic Claude 4.6 Sonnet via LangChain4j,
 * but the architecture supports swapping to OpenAI, Google, or local Ollama
 * models via configuration.</p>
 *
 * <p>Two primary operations:</p>
 * <ol>
 *   <li>Query generation: natural language → SQL/aggregation pipeline</li>
 *   <li>Rule extraction: user correction → generic business rule</li>
 * </ol>
 *
 * @author DB-Pilot
 */
public interface LlmGateway {

    /**
     * Generates a database query from a fully-assembled prompt.
     *
     * <p>The prompt is assembled by {@link com.dbpilot.core.service.PromptFactory}
     * and includes ranked schema, global rules, user habits, and user intent.</p>
     *
     * @param systemPrompt the system instructions (schema, rules, habits)
     * @param userMessage  the user's natural language query
     * @return the generated SQL or MongoDB aggregation pipeline
     */
    String generateQuery(String systemPrompt, String userMessage);

    /**
     * Extracts a generic business rule from a user's query correction.
     *
     * <p>When a user corrects a generated query, this method asks the LLM to
     * identify the underlying business rule that should be learned.</p>
     *
     * @param originalQuery   the originally generated query
     * @param correctedQuery  the user's corrected version
     * @param context         additional context (schema, table names involved)
     * @return the extracted business rule
     */
    BusinessRule extractBusinessRule(String originalQuery, String correctedQuery, String context);

    /**
     * Generates text embeddings for semantic matching.
     *
     * <p>Used by the TableRanker for semantic similarity computation.
     * Implementations may use the LLM API or a local ONNX model (BGE-small).</p>
     *
     * @param text the text to embed
     * @return the embedding vector
     */
    float[] embed(String text);

    /**
     * Checks whether the LLM provider is reachable and configured.
     *
     * @return {@code true} if the LLM is available
     */
    boolean isAvailable();

    /**
     * Returns the name of the configured LLM provider.
     *
     * @return provider name (e.g., "Anthropic Claude 4.6 Sonnet")
     */
    String getProviderName();
}
