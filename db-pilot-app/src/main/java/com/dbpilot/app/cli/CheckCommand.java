package com.dbpilot.app.cli;

import com.dbpilot.core.port.in.HealthCheckUseCase;
import com.dbpilot.core.port.in.HealthCheckUseCase.ComponentHealth;
import com.dbpilot.core.port.in.HealthCheckUseCase.HealthReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * CLI command for system health check ({@code db-pilot --check}).
 *
 * @author DB-Pilot
 */
@Component
@Command(name = "check", description = "Verify DB connectivity, MCP registration, and Knowledge Base integrity",
         mixinStandardHelpOptions = true)
@RequiredArgsConstructor
public class CheckCommand implements Callable<Integer> {

    private final HealthCheckUseCase healthCheckUseCase;

    @Override
    public Integer call() {
        System.out.println();
        System.out.println("🔍 DB-Pilot Health Check");
        System.out.println("═".repeat(50));

        HealthReport report = healthCheckUseCase.check();

        for (ComponentHealth comp : report.components()) {
            String icon = comp.healthy() ? "✅" : "❌";
            String latency = comp.latencyMs() >= 0 ? " (%dms)".formatted(comp.latencyMs()) : "";
            System.out.printf("  %s %s — %s%s%n", icon, comp.component(), comp.message(), latency);
        }

        System.out.println("═".repeat(50));
        System.out.printf("  %s %s%n%n", report.allHealthy() ? "✅" : "⚠️", report.summaryMessage());

        return report.allHealthy() ? 0 : 1;
    }
}
