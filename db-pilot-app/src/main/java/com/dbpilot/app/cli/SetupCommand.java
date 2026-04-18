package com.dbpilot.app.cli;

import com.dbpilot.core.model.DatabaseConnectionInfo;
import com.dbpilot.core.model.DatabaseType;
import com.dbpilot.core.port.out.ClientConfigurator;
import com.dbpilot.core.port.out.CredentialStore;
import com.dbpilot.core.port.out.DatabaseAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.Console;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * Interactive Setup Wizard ({@code db-pilot --setup}).
 *
 * <p>Guides the user through first-time configuration:</p>
 * <ol>
 *   <li>Set userId</li>
 *   <li>Configure database connections</li>
 *   <li>Test connectivity</li>
 *   <li>Store credentials in OS Keyring</li>
 *   <li>Auto-detect and configure AI clients</li>
 * </ol>
 *
 * @author DB-Pilot
 */
@Component
@Command(name = "setup", description = "Interactive setup wizard for DB-Pilot",
         mixinStandardHelpOptions = true)
@RequiredArgsConstructor
public class SetupCommand implements Callable<Integer> {

    private final CredentialStore credentialStore;
    private final DatabaseAdapter databaseAdapter;
    private final List<ClientConfigurator> configurators;

    @Option(names = "--non-interactive", description = "Skip interactive prompts")
    private boolean nonInteractive = false;

    @Override
    public Integer call() {
        Scanner scanner = new Scanner(System.in);
        System.out.println();
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║       🚀 DB-Pilot Setup Wizard          ║");
        System.out.println("║  Enterprise Multi-Database AI Agent      ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println();

        // Step 1: Check keyring
        if (!credentialStore.isAvailable()) {
            System.err.println("❌ OS Keyring is not available. Cannot securely store credentials.");
            return 1;
        }
        System.out.println("✅ OS Keyring available");

        // Step 2: User ID
        System.out.print("\n📛 Enter your User ID: ");
        String userId = scanner.nextLine().trim();
        if (userId.isBlank()) {
            userId = System.getProperty("user.name", "default");
            System.out.println("   Using system username: " + userId);
        }

        // Step 3: Database configuration
        boolean addMore = true;
        while (addMore) {
            configureDatabase(scanner);
            System.out.print("\n➕ Add another database? (y/N): ");
            addMore = scanner.nextLine().trim().equalsIgnoreCase("y");
        }

        // Step 4: Auto-configure AI clients
        System.out.println("\n🔍 Detecting AI clients...");
        String execPath = resolveExecutablePath();

        for (ClientConfigurator configurator : configurators) {
            if (configurator.isInstalled()) {
                if (configurator.isAlreadyConfigured()) {
                    System.out.println("  ✅ " + configurator.getClientName() + " — already configured");
                } else {
                    System.out.print("  📎 " + configurator.getClientName() + " detected. Configure? (Y/n): ");
                    String answer = scanner.nextLine().trim();
                    if (answer.isBlank() || answer.equalsIgnoreCase("y")) {
                        String result = configurator.configure(execPath);
                        System.out.println("  " + result);
                    }
                }
            } else {
                System.out.println("  ⬜ " + configurator.getClientName() + " — not detected");
            }
        }

        // Done
        System.out.println("\n╔══════════════════════════════════════════╗");
        System.out.println("║  ✅ Setup complete!                      ║");
        System.out.println("║  Run 'db-pilot --check' to verify        ║");
        System.out.println("╚══════════════════════════════════════════╝");
        return 0;
    }

    private void configureDatabase(Scanner scanner) {
        System.out.println("\n── Configure Database Connection ──");

        System.out.print("  Alias (e.g., prod-postgres): ");
        String alias = scanner.nextLine().trim();

        System.out.println("  Type: 1=PostgreSQL, 2=Oracle, 3=MySQL, 4=MariaDB, 5=MongoDB");
        System.out.print("  Select [1-5]: ");
        DatabaseType dbType = switch (scanner.nextLine().trim()) {
            case "1" -> DatabaseType.POSTGRESQL;
            case "2" -> DatabaseType.ORACLE;
            case "3" -> DatabaseType.MYSQL;
            case "4" -> DatabaseType.MARIADB;
            case "5" -> DatabaseType.MONGODB;
            default -> {
                System.out.println("  Invalid. Defaulting to PostgreSQL.");
                yield DatabaseType.POSTGRESQL;
            }
        };

        System.out.print("  Host [localhost]: ");
        String host = scanner.nextLine().trim();
        if (host.isBlank()) host = "localhost";

        int defaultPort = switch (dbType) {
            case POSTGRESQL -> 5432;
            case ORACLE -> 1521;
            case MYSQL, MARIADB -> 3306;
            case MONGODB -> 27017;
        };
        System.out.print("  Port [%d]: ".formatted(defaultPort));
        String portStr = scanner.nextLine().trim();
        int port = portStr.isBlank() ? defaultPort : Integer.parseInt(portStr);

        System.out.print("  Database Name: ");
        String dbName = scanner.nextLine().trim();

        System.out.print("  Schema (optional): ");
        String schema = scanner.nextLine().trim();

        System.out.print("  Username: ");
        String username = scanner.nextLine().trim();

        System.out.print("  Password: ");
        // Try to use Console for hidden input, fall back to Scanner
        String password;
        Console console = System.console();
        if (console != null) {
            password = new String(console.readPassword());
        } else {
            password = scanner.nextLine().trim();
        }

        DatabaseConnectionInfo connInfo = DatabaseConnectionInfo.builder()
                .alias(alias)
                .databaseType(dbType)
                .host(host)
                .port(port)
                .databaseName(dbName)
                .schema(schema.isBlank() ? null : schema)
                .username(username)
                .password(password)
                .build();

        // Test connection
        System.out.print("  🔄 Testing connection...");
        if (databaseAdapter.testConnection(connInfo)) {
            System.out.println(" ✅ Connected!");
            credentialStore.store(alias, connInfo);
            System.out.println("  🔒 Credentials stored in OS Keyring");
        } else {
            System.out.println(" ❌ Connection failed!");
            System.out.print("  Store anyway? (y/N): ");
            if (scanner.nextLine().trim().equalsIgnoreCase("y")) {
                credentialStore.store(alias, connInfo);
            }
        }
    }

    private String resolveExecutablePath() {
        // Try to find our own JAR or native binary
        String javaCommand = ProcessHandle.current().info().command().orElse("java");
        String jarPath = System.getProperty("java.class.path", "db-pilot.jar");
        return javaCommand + " -jar " + jarPath;
    }
}
