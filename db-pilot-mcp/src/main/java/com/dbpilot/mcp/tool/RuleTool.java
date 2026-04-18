package com.dbpilot.mcp.tool;

import com.dbpilot.core.model.BusinessRule;
import com.dbpilot.core.port.out.KnowledgeStoreAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * MCP Tool: Rule management.
 *
 * <p>Allows an external agent to seamlessly insert business rules 
 * when operating without an internal LLM configured.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleTool {

    private final KnowledgeStoreAdapter knowledgeStoreAdapter;

    /**
     * Saves a business rule directly.
     */
    public String addRule(String ruleText, String databaseAlias, String userId) {
        log.info("[MCP] addRule invoked: {}", ruleText);
        try {
            BusinessRule rule = BusinessRule.builder()
                    .id(UUID.randomUUID().toString())
                    .ruleText(ruleText)
                    .scope(BusinessRule.Scope.USER) // Default to USER for safety
                    .userId(userId != null ? userId : "default")
                    .databaseAlias(databaseAlias)
                    .createdAt(Instant.now())
                    .applicationCount(0)
                    .confidence(1.0) // External AI assumed 100% confidence
                    .build();
            
            knowledgeStoreAdapter.saveUserRule(rule.getUserId(), rule);
            return "✅ Rule added successfully: " + ruleText;
        } catch (Exception e) {
            log.error("[MCP] addRule failed: {}", e.getMessage(), e);
            return "❌ Failed to add rule: " + e.getMessage();
        }
    }
}
