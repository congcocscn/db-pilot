package com.dbpilot.app;

import com.dbpilot.app.cli.DbPilotCli;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/**
 * DB-Pilot application entry point.
 *
 * <p>Enterprise-Grade Multi-Database AI Agent — translates natural language
 * into precise database queries via the Model Context Protocol (MCP).</p>
 *
 * <p>Powered by Anthropic Claude 4.6 Sonnet. Supports Oracle, PostgreSQL,
 * MySQL, MariaDB, and MongoDB.</p>
 *
 * @author DB-Pilot
 */
@SpringBootApplication(exclude = {MongoAutoConfiguration.class})
@ComponentScan(basePackages = {
        "com.dbpilot.app",
        "com.dbpilot.infrastructure",
        "com.dbpilot.mcp"
})
public class DbPilotApplication implements CommandLineRunner, ExitCodeGenerator {

    public static final java.io.PrintStream MCP_OUT = System.out;

    private final IFactory factory;
    private final DbPilotCli cli;
    private int exitCode = 0;

    public DbPilotApplication(IFactory factory, DbPilotCli cli) {
        this.factory = factory;
        this.cli = cli;
    }

    public static void main(String[] args) {
        boolean isMcp = false;
        for (String arg : args) {
            if (arg.contains("mcp.enabled=true") || arg.equals("--mcp")) {
                isMcp = true;
                break;
            }
        }

        if (isMcp) {
            // IMPORTANT: Force all standard output (like Logback/Spring logs) to stderr.
            // stdout is strictly reserved for the MCP JSON-RPC protocol.
            System.setOut(System.err);
        }

        SpringApplication app = new SpringApplication(DbPilotApplication.class);

        // Redirect banner + logs to stderr (stdout reserved for MCP STDIO)
        System.setProperty("spring.main.banner-mode", "log");

        // We call System.exit to return the code from Picocli
        System.exit(SpringApplication.exit(app.run(args)));
    }

    @Override
    public void run(String... args) {
        // If MCP mode is enabled via property, the McpServerConfig handles it.
        // We skip Picocli execution to avoid blocking or exceptions.
        boolean isMcp = false;
        for (String arg : args) {
            if (arg.contains("mcp.enabled=true") || arg.equals("--mcp")) {
                isMcp = true;
                break;
            }
        }

        if (!isMcp) {
            exitCode = new CommandLine(cli, factory).execute(args);
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
