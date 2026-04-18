package com.dbpilot.core.port.in;

import java.util.Map;

/**
 * Inbound port for system health checking ({@code db-pilot --check}).
 *
 * <p>Verifies:</p>
 * <ul>
 *   <li>Database connectivity for all configured connections</li>
 *   <li>MCP registration status in AI clients</li>
 *   <li>Knowledge Base integrity</li>
 *   <li>LLM provider availability</li>
 *   <li>Credential store accessibility</li>
 * </ul>
 *
 * @author DB-Pilot
 */
public interface HealthCheckUseCase {

    /**
     * Individual component health status.
     *
     * @param component   the component name (e.g., "PostgreSQL: prod-db")
     * @param healthy     whether the component is healthy
     * @param message     human-readable status message
     * @param latencyMs   response latency in milliseconds (-1 if not applicable)
     */
    record ComponentHealth(
            String component,
            boolean healthy,
            String message,
            long latencyMs
    ) {}

    /**
     * Aggregated health report.
     *
     * @param allHealthy       whether all components are healthy
     * @param components       individual component status
     * @param summaryMessage   one-line summary
     */
    record HealthReport(
            boolean allHealthy,
            java.util.List<ComponentHealth> components,
            String summaryMessage
    ) {}

    /**
     * Executes a full system health check.
     *
     * @return the aggregated health report
     */
    HealthReport check();
}
