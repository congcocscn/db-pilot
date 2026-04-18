package com.dbpilot.app.cli;

import com.dbpilot.core.port.in.TranslateQueryUseCase;
import com.dbpilot.core.port.in.TranslateQueryUseCase.TranslateCommand;
import com.dbpilot.core.port.in.TranslateQueryUseCase.TranslateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

/**
 * CLI command for dry-run mode ({@code db-pilot dry-run "query"}).
 *
 * <p>Generates a query from natural language and validates it using EXPLAIN
 * without actually executing it. Shows the execution plan for review.</p>
 *
 * @author DB-Pilot
 */
@Component
@Command(name = "dry-run", description = "Generate and validate a query without executing it (uses EXPLAIN)",
         mixinStandardHelpOptions = true)
@RequiredArgsConstructor
public class DryRunCommand implements Callable<Integer> {

    private final TranslateQueryUseCase translateQueryUseCase;

    @Parameters(index = "0", description = "Natural language query (e.g., \"show all active users\")")
    private String naturalQuery;

    @Option(names = {"-d", "--database"}, description = "Database alias", required = true)
    private String databaseAlias;

    @Option(names = {"-u", "--user"}, description = "User ID", defaultValue = "${user.name}")
    private String userId;

    @Override
    public Integer call() {
        System.out.println();
        System.out.println("🧪 DB-Pilot Dry Run");
        System.out.println("═".repeat(50));
        System.out.printf("  Database: %s%n", databaseAlias);
        System.out.printf("  Intent:   %s%n", naturalQuery);
        System.out.println("═".repeat(50));

        try {
            TranslateCommand command = new TranslateCommand(
                    userId, databaseAlias, naturalQuery, false, true);

            TranslateResponse response = translateQueryUseCase.translate(command);

            // Display generated query
            System.out.println("\n📝 Generated Query:");
            System.out.println("┌" + "─".repeat(48) + "┐");
            for (String line : response.generatedQuery().split("\n")) {
                System.out.printf("│ %-46s │%n", line);
            }
            System.out.println("└" + "─".repeat(48) + "┘");

            // Display explain plan
            if (response.queryResult() != null) {
                var result = response.queryResult();
                if (result.isSuccess()) {
                    System.out.println("\n✅ Query is valid!");
                    if (result.getExplainPlan() != null) {
                        System.out.println("\n📊 Execution Plan:");
                        System.out.println(result.getExplainPlan());
                    }
                } else {
                    System.out.println("\n❌ Query validation failed:");
                    System.out.println("   " + result.getErrorMessage());
                    return 1;
                }
            }

            // Display explanation
            if (response.explanation() != null) {
                System.out.println("\n" + response.explanation());
            }

            return 0;

        } catch (Exception e) {
            System.err.println("\n❌ Error: " + e.getMessage());
            return 1;
        }
    }
}
