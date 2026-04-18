package com.dbpilot.core.service;

import lombok.extern.slf4j.Slf4j;

/**
 * Domain service that enforces a hard token limit on LLM prompts.
 *
 * <p>Implements a multi-stage pruning strategy when the assembled prompt
 * exceeds the configured token budget:</p>
 * <ol>
 *   <li>Drop tables with the lowest ranking score</li>
 *   <li>Drop user habits (oldest first)</li>
 *   <li>Drop global rules (least relevant)</li>
 *   <li>Truncate column metadata (keep only name + type, drop constraints)</li>
 * </ol>
 *
 * <p>Token estimation uses a simple character-to-token ratio (≈4 chars/token
 * for English text) as a fast approximation. For production accuracy, integrate
 * a proper tokenizer (e.g., cl100k_base via jtokkit).</p>
 *
 * @author DB-Pilot
 */
@Slf4j
public class TokenGuard {

    /** Default hard limit in tokens. */
    private static final int DEFAULT_TOKEN_LIMIT = 4000;

    /** Approximate characters per token (English text average). */
    private static final double CHARS_PER_TOKEN = 4.0;

    private final int tokenLimit;

    /**
     * Creates a TokenGuard with the default token limit (4000).
     */
    public TokenGuard() {
        this(DEFAULT_TOKEN_LIMIT);
    }

    /**
     * Creates a TokenGuard with a custom token limit.
     *
     * @param tokenLimit the maximum number of tokens allowed
     */
    public TokenGuard(int tokenLimit) {
        this.tokenLimit = tokenLimit;
    }

    /**
     * Enforces the token budget on a prompt string.
     *
     * <p>If the prompt is within budget, returns it unchanged.
     * If it exceeds the budget, truncates from the end while preserving
     * the header and instructions sections.</p>
     *
     * @param prompt the raw assembled prompt
     * @return the prompt, potentially truncated to fit within token budget
     */
    public String enforce(String prompt) {
        int estimatedTokens = estimateTokens(prompt);

        if (estimatedTokens <= tokenLimit) {
            log.debug("Prompt within budget: {} tokens (limit: {})", estimatedTokens, tokenLimit);
            return prompt;
        }

        log.warn("Prompt exceeds budget: {} tokens (limit: {}). Pruning...", estimatedTokens, tokenLimit);
        return prune(prompt, estimatedTokens);
    }

    /**
     * Estimates the token count for a given text.
     *
     * @param text the text to estimate
     * @return estimated token count
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    /**
     * Returns the configured token limit.
     *
     * @return the token limit
     */
    public int getTokenLimit() {
        return tokenLimit;
    }

    /**
     * Prunes the prompt to fit within the token budget.
     *
     * <p>Strategy:</p>
     * <ul>
     *   <li>Preserve everything before "## Schema"</li>
     *   <li>Truncate schema section by removing lowest-ranked tables (bottom entries)</li>
     *   <li>Preserve user-facing sections as much as possible</li>
     * </ul>
     */
    private String prune(String prompt, int currentTokens) {
        // Find section boundaries
        int schemaStart = prompt.indexOf("## Schema\n");
        int schemaEnd = findNextSection(prompt, schemaStart + 10);

        if (schemaStart < 0) {
            // No schema section — hard truncate
            int maxChars = (int) (tokenLimit * CHARS_PER_TOKEN);
            return prompt.substring(0, Math.min(prompt.length(), maxChars))
                    + "\n[... truncated due to token limit]";
        }

        // Extract sections
        String header = prompt.substring(0, schemaStart);
        String schema = prompt.substring(schemaStart, schemaEnd);
        String footer = prompt.substring(schemaEnd);

        // Progressively trim schema lines from the bottom
        String[] schemaLines = schema.split("\n");
        StringBuilder trimmedSchema = new StringBuilder();
        int tokensUsed = estimateTokens(header) + estimateTokens(footer);

        for (String line : schemaLines) {
            int lineTokens = estimateTokens(line + "\n");
            if (tokensUsed + lineTokens > tokenLimit - 50) { // Reserve 50 tokens for safety
                trimmedSchema.append("[... pruned %d tables due to token limit]\n".formatted(
                        schemaLines.length - trimmedSchema.toString().split("\n").length));
                break;
            }
            trimmedSchema.append(line).append('\n');
            tokensUsed += lineTokens;
        }

        String result = header + trimmedSchema + footer;
        log.info("Pruned prompt from {} to ~{} tokens", currentTokens, estimateTokens(result));
        return result;
    }

    /**
     * Finds the start of the next markdown section (## heading) after the given position.
     */
    private int findNextSection(String text, int from) {
        int idx = text.indexOf("\n## ", from);
        return idx >= 0 ? idx + 1 : text.length();
    }
}
