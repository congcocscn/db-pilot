package com.dbpilot.mcp.config;

import com.dbpilot.mcp.tool.HealthTool;
import com.dbpilot.mcp.tool.QueryTool;
import com.dbpilot.mcp.tool.RuleTool;
import com.dbpilot.mcp.tool.SchemaExplorerTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.*;
import java.util.Map;

/**
 * MCP Server configuration for STDIO transport.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class McpServerConfig {

    private final QueryTool queryTool;
    private final SchemaExplorerTool schemaExplorerTool;
    private final HealthTool healthTool;
    private final RuleTool ruleTool;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    @ConditionalOnProperty(name = "dbpilot.mcp.enabled", havingValue = "true")
    public CommandLineRunner mcpServerRunner() {
        return args -> {
            log.info("Starting DB-Pilot MCP Server (STDIO transport)...");
            runStdioLoop();
        };
    }

    private void runStdioLoop() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new java.io.FileOutputStream(java.io.FileDescriptor.out))), true);

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    Map<String, Object> request = objectMapper.readValue(line, Map.class);
                    Map<String, Object> response = handleRequest(request);
                    writer.println(objectMapper.writeValueAsString(response));
                    writer.flush();
                } catch (Exception e) {
                    log.error("Error processing MCP request: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("MCP STDIO loop terminated: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleRequest(Map<String, Object> request) {
        String method = (String) request.get("method");
        Object id = request.get("id");
        Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", Map.of());

        Object result = switch (method) {
            case "initialize" -> handleInitialize();
            case "tools/list" -> handleToolsList();
            case "tools/call" -> handleToolCall(params);
            default -> Map.of("error", "Unknown method: " + method);
        };

        return Map.of(
                "jsonrpc", "2.0",
                "id", id != null ? id : "null",
                "result", result
        );
    }

    private Map<String, Object> handleInitialize() {
        return Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of("tools", Map.of("listChanged", false)),
                "serverInfo", Map.of("name", "db-pilot", "version", "1.0.0")
        );
    }

    private Map<String, Object> handleToolsList() {
        return Map.of("tools", java.util.List.of(
                Map.of(
                        "name", "translate_query",
                        "description", "Translate natural language to SQL/MongoDB and execute",
                        "inputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "intent", Map.of("type", "string"),
                                        "database_alias", Map.of("type", "string"),
                                        "execute", Map.of("type", "boolean", "default", false)
                                ),
                                "required", java.util.List.of("intent", "database_alias")
                        )
                ),
                Map.of(
                        "name", "execute_sql",
                        "description", "Execute raw SQL সরাসরি",
                        "inputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "database_alias", Map.of("type", "string"),
                                        "sql", Map.of("type", "string")
                                ),
                                "required", java.util.List.of("database_alias", "sql")
                        )
                ),
                Map.of(
                        "name", "explore_schema",
                        "description", "Explore DB schema",
                        "inputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "database_alias", Map.of("type", "string"),
                                        "command", Map.of("type", "string", "default", "tables")
                                ),
                                "required", java.util.List.of("database_alias")
                        )
                ),
                Map.of(
                        "name", "add_rule",
                        "description", "Add a business rule or query habit manually directly to knowledge base",
                        "inputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "rule_text", Map.of("type", "string"),
                                        "database_alias", Map.of("type", "string")
                                ),
                                "required", java.util.List.of("rule_text")
                        )
                ),
                Map.of(
                        "name", "health_check",
                        "description", "Check health",
                        "inputSchema", Map.of("type", "object", "properties", Map.of())
                )
        ));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolCall(Map<String, Object> params) {
        String toolName = (String) params.get("name");
        Map<String, Object> args = (Map<String, Object>) params.getOrDefault("arguments", Map.of());

        String result = switch (toolName) {
            case "translate_query" -> queryTool.translateQuery(
                    (String) args.get("intent"),
                    (String) args.get("database_alias"),
                    "default",
                    Boolean.TRUE.equals(args.get("execute")),
                    false
            );
            case "execute_sql" -> queryTool.executeRawSql(
                    (String) args.get("database_alias"),
                    (String) args.get("sql")
            );
            case "explore_schema" -> schemaExplorerTool.explore(
                    (String) args.get("database_alias"),
                    (String) args.getOrDefault("command", "tables")
            );
            case "add_rule" -> ruleTool.addRule(
                    (String) args.get("rule_text"),
                    (String) args.get("database_alias"),
                    "default"
            );
            case "health_check" -> healthTool.check();
            default -> "Unknown tool: " + toolName;
        };

        return Map.of(
                "content", java.util.List.of(
                        Map.of("type", "text", "text", result)
                )
        );
    }
}
