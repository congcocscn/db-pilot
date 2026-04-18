package com.dbpilot.app.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * Main CLI router for DB-Pilot.
 *
 * <p>Root command that dispatches to subcommands:</p>
 * <ul>
 *   <li>{@code db-pilot setup} — interactive setup wizard</li>
 *   <li>{@code db-pilot check} — system health check</li>
 *   <li>{@code db-pilot query} — interactive query mode</li>
 * </ul>
 *
 * @author DB-Pilot
 */
@Component
@Command(
        name = "db-pilot",
        description = "Enterprise-Grade Multi-Database AI Agent",
        version = "DB-Pilot 1.0.0-SNAPSHOT",
        mixinStandardHelpOptions = true,
        subcommands = {
                SetupCommand.class,
                CheckCommand.class,
                DryRunCommand.class
        }
)
public class DbPilotCli implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║  🛢️  DB-Pilot v1.0.0                        ║");
        System.out.println("║  Enterprise Multi-Database AI Agent          ║");
        System.out.println("║  Powered by Anthropic Claude 4.6 Sonnet     ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  db-pilot setup    — Interactive setup wizard");
        System.out.println("  db-pilot check    — System health check");
        System.out.println("  db-pilot --help   — Show all commands");
        System.out.println();
        System.out.println("MCP Mode:");
        System.out.println("  db-pilot --mcp    — Start as MCP server (STDIO transport)");
        System.out.println();
        return 0;
    }
}
