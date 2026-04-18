package com.dbpilot.mcp.tool;

import com.dbpilot.core.port.in.HealthCheckUseCase;
import com.dbpilot.core.port.in.HealthCheckUseCase.HealthReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MCP Tool: System health check.
 *
 * <p>Verifies database connectivity, MCP registration, Knowledge Base integrity,
 * and LLM provider availability.</p>
 *
 * @author DB-Pilot
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthTool {

    private final HealthCheckUseCase healthCheckUseCase;

    /**
     * Runs a comprehensive health check.
     *
     * @return formatted health report
     */
    public String check() {
        log.info("[MCP] healthCheck invoked");

        try {
            HealthReport report = healthCheckUseCase.check();
            return formatReport(report);
        } catch (Exception e) {
            log.error("[MCP] healthCheck failed: {}", e.getMessage(), e);
            return "❌ Health check failed: " + e.getMessage();
        }
    }

    private String formatReport(HealthReport report) {
        var sb = new StringBuilder();
        sb.append(report.allHealthy() ? "✅ " : "⚠️ ");
        sb.append("DB-Pilot Health: ").append(report.summaryMessage()).append("\n\n");

        for (var comp : report.components()) {
            sb.append(comp.healthy() ? "  ✅ " : "  ❌ ");
            sb.append(comp.component());
            sb.append(" — ").append(comp.message());
            if (comp.latencyMs() >= 0) {
                sb.append(" (%dms)".formatted(comp.latencyMs()));
            }
            sb.append('\n');
        }

        return sb.toString();
    }
}
