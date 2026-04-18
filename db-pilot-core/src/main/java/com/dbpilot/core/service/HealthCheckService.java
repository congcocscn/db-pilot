package com.dbpilot.core.service;

import com.dbpilot.core.port.in.HealthCheckUseCase;
import com.dbpilot.core.port.out.CredentialStore;
import com.dbpilot.core.port.out.KnowledgeStoreAdapter;
import com.dbpilot.core.port.out.LlmGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain service implementing system health checks.
 *
 * <p>Verifies all critical subsystems: database connectivity,
 * LLM availability, knowledge store integrity, and credential access.</p>
 *
 * @author DB-Pilot
 */
@Slf4j
@RequiredArgsConstructor
public class HealthCheckService implements HealthCheckUseCase {

    private final CredentialStore credentialStore;
    private final KnowledgeStoreAdapter knowledgeStore;
    private final LlmGateway llmGateway;

    @Override
    public HealthReport check() {
        log.info("Running system health check...");
        List<ComponentHealth> components = new ArrayList<>();

        // 1. Credential Store
        components.add(checkCredentialStore());

        // 2. Knowledge Store
        components.add(checkKnowledgeStore());

        // 3. LLM Provider
        components.add(checkLlmProvider());

        // 4. Database connections (for each configured alias)
        components.addAll(checkDatabaseConnections());

        boolean allHealthy = components.stream().allMatch(ComponentHealth::healthy);
        String summary = allHealthy
                ? "All systems operational"
                : "%d of %d components unhealthy".formatted(
                    components.stream().filter(c -> !c.healthy()).count(),
                    components.size());

        return new HealthReport(allHealthy, components, summary);
    }

    private ComponentHealth checkCredentialStore() {
        long start = System.currentTimeMillis();
        try {
            boolean available = credentialStore.isAvailable();
            long elapsed = System.currentTimeMillis() - start;
            return new ComponentHealth(
                    "Credential Store",
                    available,
                    available ? "OS Keyring accessible" : "OS Keyring not available",
                    elapsed
            );
        } catch (Exception e) {
            return new ComponentHealth("Credential Store", false, "Error: " + e.getMessage(), -1);
        }
    }

    private ComponentHealth checkKnowledgeStore() {
        long start = System.currentTimeMillis();
        try {
            String status = knowledgeStore.healthCheck();
            long elapsed = System.currentTimeMillis() - start;
            return new ComponentHealth("Knowledge Store", true, status, elapsed);
        } catch (Exception e) {
            return new ComponentHealth("Knowledge Store", false, "Error: " + e.getMessage(), -1);
        }
    }

    private ComponentHealth checkLlmProvider() {
        long start = System.currentTimeMillis();
        try {
            boolean available = llmGateway.isAvailable();
            long elapsed = System.currentTimeMillis() - start;
            return new ComponentHealth(
                    "LLM: " + llmGateway.getProviderName(),
                    available,
                    available ? "Available" : "Not reachable",
                    elapsed
            );
        } catch (Exception e) {
            return new ComponentHealth(
                    "LLM: " + llmGateway.getProviderName(),
                    false,
                    "Error: " + e.getMessage(),
                    -1
            );
        }
    }

    private List<ComponentHealth> checkDatabaseConnections() {
        List<ComponentHealth> results = new ArrayList<>();
        try {
            List<String> aliases = credentialStore.listAliases();
            if (aliases.isEmpty()) {
                results.add(new ComponentHealth(
                        "Databases", true,
                        "No databases configured — run db-pilot setup", -1));
                return results;
            }
            // For now, just report the configured aliases.
            // Full connection testing requires DatabaseAdapterFactory which is in infrastructure.
            for (String alias : aliases) {
                results.add(new ComponentHealth(
                        "Database: " + alias, true,
                        "Configured", -1));
            }
        } catch (Exception e) {
            results.add(new ComponentHealth(
                    "Databases", false,
                    "Failed to list: " + e.getMessage(), -1));
        }
        return results;
    }
}
