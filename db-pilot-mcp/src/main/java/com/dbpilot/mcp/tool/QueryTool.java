package com.dbpilot.mcp.tool;

import com.dbpilot.core.port.in.TranslateQueryUseCase;
import com.dbpilot.core.port.in.TranslateQueryUseCase.TranslateCommand;
import com.dbpilot.core.port.in.TranslateQueryUseCase.TranslateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MCP Tool: Translates natural language into a database query.
 *
 * <p>This is the primary tool exposed via the Model Context Protocol.
 * AI clients (Claude Desktop, Cursor) call this tool to generate
 * SQL or MongoDB aggregation pipelines from user intent.</p>
 *
 * @author DB-Pilot
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryTool {

    private final TranslateQueryUseCase translateQueryUseCase;
    private final com.dbpilot.core.port.out.DatabaseAdapter databaseAdapter;
    private final com.dbpilot.core.port.out.CredentialStore credentialStore;

    /**
     * Translates natural language to a database query and optionally executes it.
     *
     * @param intent        natural language description of the data desired
     * @param databaseAlias the target database connection alias
     * @param userId        the user making the request
     * @param execute       whether to execute the query after generation
     * @param dryRun        whether to run EXPLAIN instead of executing
     * @return formatted result containing the generated query and execution results
     */
    public String translateQuery(String intent,
                                  String databaseAlias,
                                  String userId,
                                  boolean execute,
                                  boolean dryRun) {

        log.info("[MCP] translateQuery — user: '{}', db: '{}', intent: '{}'",
                userId, databaseAlias, intent);

        try {
            TranslateCommand command = new TranslateCommand(
                    userId, databaseAlias, intent, execute, dryRun);

            TranslateResponse response = translateQueryUseCase.translate(command);

            return formatResponse(response);

        } catch (Exception e) {
            log.error("[MCP] translateQuery failed: {}", e.getMessage(), e);
            return "❌ Error: " + e.getMessage();
        }
    }

    /**
     * Executes a raw SQL command directly.
     */
    public String executeRawSql(String databaseAlias, String sql) {
        log.info("[MCP] executeRawSql — db: '{}', sql: '{}'", databaseAlias, sql);
        try {
            com.dbpilot.core.model.DatabaseConnectionInfo connInfo = credentialStore.retrieve(databaseAlias);
            if (connInfo == null) {
                return "❌ Error: Database alias '" + databaseAlias + "' not found.";
            }

            com.dbpilot.core.model.QueryResult result = databaseAdapter.executeQuery(connInfo, sql);
            
            if (result.getErrorMessage() != null) {
                return "❌ Execution Failed:\n" + result.getErrorMessage();
            }

            var sb = new StringBuilder();
            sb.append("✅ SQL Executed Successfully on '").append(databaseAlias).append("'\n\n");
            
            if (result.getRowCount() >= 0) {
                sb.append("Rows affected/returned: ").append(result.getRowCount()).append("\n");
            }
            
            if (!result.getRows().isEmpty()) {
                sb.append("\n### Results:\n").append(formatResultTable(result));
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("[MCP] executeRawSql failed", e);
            return "❌ Error: " + e.getMessage();
        }
    }

    private String formatResponse(TranslateResponse response) {
        var sb = new StringBuilder();
        sb.append("## Generated Query\n```sql\n");
        sb.append(response.generatedQuery());
        sb.append("\n```\n\n");

        if (response.explanation() != null) {
            sb.append(response.explanation()).append("\n\n");
        }

        if (response.queryResult() != null) {
            var result = response.queryResult();
            sb.append(result.toSummary()).append('\n');

            if (result.isDryRun() && result.getExplainPlan() != null) {
                sb.append("### Explain Plan\n```\n");
                sb.append(result.getExplainPlan());
                sb.append("```\n");
            } else if (result.isSuccess() && result.getRows() != null) {
                // Format as table
                sb.append(formatResultTable(result));
            }

            if (result.getErrorMessage() != null) {
                sb.append("### Error\n").append(result.getErrorMessage()).append('\n');
            }
        }

        return sb.toString();
    }

    private String formatResultTable(com.dbpilot.core.model.QueryResult result) {
        if (result.getColumnNames().isEmpty() || result.getRows().isEmpty()) return "";

        var sb = new StringBuilder();
        var cols = result.getColumnNames();

        // Header
        sb.append("| ").append(String.join(" | ", cols)).append(" |\n");
        sb.append("| ").append(cols.stream().map(c -> "---").collect(java.util.stream.Collectors.joining(" | "))).append(" |\n");

        // Rows (limit to 20)
        result.getRows().stream().limit(20).forEach(row -> {
            sb.append("| ");
            sb.append(cols.stream()
                    .map(c -> String.valueOf(row.getOrDefault(c, "")))
                    .collect(java.util.stream.Collectors.joining(" | ")));
            sb.append(" |\n");
        });

        if (result.getRowCount() > 20) {
            sb.append("_... and %d more rows_\n".formatted(result.getRowCount() - 20));
        }

        return sb.toString();
    }
}
