package com.dbpilot.infrastructure.adapter.llm;

import com.dbpilot.core.model.BusinessRule;
import com.dbpilot.core.port.out.LlmGateway;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@link LlmGateway} implementation using LangChain4j.
 *
 * <p>Default provider: <strong>Anthropic Claude 4.6 Sonnet</strong>.
 * The provider is swappable via configuration — architecture remains agnostic.</p>
 *
 * @author DB-Pilot
 */
@Slf4j
public class LangChain4jLlmGateway implements LlmGateway {

    private final ChatLanguageModel chatModel;
    private final String providerName;

    /**
     * Creates a gateway with the given LangChain4j chat model.
     *
     * @param chatModel    the LangChain4j chat model
     * @param providerName human-readable provider name
     */
    public LangChain4jLlmGateway(ChatLanguageModel chatModel, String providerName) {
        this.chatModel = chatModel;
        this.providerName = providerName;
    }

    /**
     * Creates the default gateway using Anthropic Claude 4.6 Sonnet.
     *
     * @param apiKey the Anthropic API key
     * @return configured gateway instance
     */
    public static LangChain4jLlmGateway anthropicDefault(String apiKey) {
        ChatLanguageModel model = AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName("claude-sonnet-4-20250514")
                .maxTokens(4096)
                .temperature(0.0) // Deterministic for SQL generation
                .build();
        return new LangChain4jLlmGateway(model, "Anthropic Claude 4.6 Sonnet");
    }

    @Override
    public String generateQuery(String systemPrompt, String userMessage) {
        log.debug("Generating query via {} — intent: '{}'", providerName, truncate(userMessage, 100));

        List<ChatMessage> messages = List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userMessage)
        );
        ChatResponse response = chatModel.chat(messages);

        String query = response.aiMessage().text().trim();

        // Strip markdown code fences if the LLM wraps the query
        query = stripCodeFences(query);

        log.debug("Generated query: {}", truncate(query, 200));
        return query;
    }

    @Override
    public BusinessRule extractBusinessRule(String originalQuery, String correctedQuery, String context) {
        String systemPrompt = """
                You are a database business rule extractor for DB-Pilot.
                
                Given an original query and a user's corrected version, extract the underlying
                business rule that should be remembered for future query generation.
                
                Rules should be:
                - Generic and reusable (not tied to specific values)
                - Expressed in natural language
                - Actionable for SQL/query generation
                
                Respond with ONLY the rule text. One sentence. No explanation.
                
                If the correction is purely syntactic (typo fix, formatting), respond with: NONE
                """;

        String userMsg = """
                Original query:
                %s
                
                Corrected query:
                %s
                
                Schema context:
                %s
                """.formatted(originalQuery, correctedQuery, context);

        List<ChatMessage> messages = List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userMsg)
        );
        ChatResponse response = chatModel.chat(messages);

        String ruleText = response.aiMessage().text().trim();

        if ("NONE".equalsIgnoreCase(ruleText) || ruleText.isBlank()) {
            log.info("No business rule extracted from correction (syntactic change only)");
            return null;
        }

        log.info("Extracted business rule: {}", ruleText);

        return BusinessRule.builder()
                .id(UUID.randomUUID().toString())
                .ruleText(ruleText)
                .scope(BusinessRule.Scope.USER) // Default to USER scope; can be promoted
                .confidence(0.8) // Default confidence for LLM-extracted rules
                .createdAt(Instant.now())
                .applicationCount(0)
                .build();
    }

    @Override
    public float[] embed(String text) {
        // Embedding will be handled by EmbeddingService (ONNX/API dual mode)
        // This is a fallback using the chat model (not ideal for embeddings)
        log.warn("Using chat model for embeddings — consider using EmbeddingService instead");
        throw new UnsupportedOperationException(
                "Use EmbeddingService for vector embeddings. This gateway handles chat only.");
    }

    @Override
    public boolean isAvailable() {
        try {
            ChatResponse response = chatModel.chat(List.of(
                    UserMessage.from("ping")
            ));
            return response.aiMessage() != null;
        } catch (Exception e) {
            log.warn("LLM availability check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    /**
     * Strips markdown code fences from LLM output.
     */
    private String stripCodeFences(String text) {
        if (text.startsWith("```")) {
            // Remove opening fence (might include language tag like ```sql)
            int firstNewline = text.indexOf('\n');
            if (firstNewline > 0) {
                text = text.substring(firstNewline + 1);
            }
            // Remove closing fence
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            return text.trim();
        }
        return text;
    }

    private String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
