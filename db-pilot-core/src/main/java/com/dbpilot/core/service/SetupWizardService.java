package com.dbpilot.core.service;

import com.dbpilot.core.model.DatabaseConnectionInfo;
import com.dbpilot.core.port.in.SetupWizardUseCase;
import com.dbpilot.core.port.out.ClientConfigurator;
import com.dbpilot.core.port.out.CredentialStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain service implementing the interactive setup wizard.
 *
 * <p>Orchestrates first-time configuration: database credentials,
 * connection testing, keyring storage, and AI client auto-configuration.</p>
 *
 * @author DB-Pilot
 */
@Slf4j
@RequiredArgsConstructor
public class SetupWizardService implements SetupWizardUseCase {

    private final CredentialStore credentialStore;
    private final List<ClientConfigurator> clientConfigurators;

    @Override
    public SetupResult runSetup() {
        log.info("Starting setup wizard...");
        List<String> configuredDatabases = new ArrayList<>();
        List<String> configuredClients = new ArrayList<>();
        List<String> messages = new ArrayList<>();

        // Verify credential store is available
        if (!credentialStore.isAvailable()) {
            messages.add("❌ OS Keyring is not available. Cannot store credentials securely.");
            return new SetupResult(false, null, configuredDatabases, configuredClients, messages);
        }
        messages.add("✅ OS Keyring is accessible");

        // List existing configured databases
        List<String> existingAliases = credentialStore.listAliases();
        if (!existingAliases.isEmpty()) {
            messages.add("ℹ️ Found %d existing database(s): %s"
                    .formatted(existingAliases.size(), String.join(", ", existingAliases)));
        }

        // Auto-configure AI clients
        for (ClientConfigurator configurator : clientConfigurators) {
            try {
                if (configurator.isInstalled()) {
                    if (configurator.isAlreadyConfigured()) {
                        messages.add("ℹ️ %s — already configured".formatted(configurator.getClientName()));
                        configuredClients.add(configurator.getClientName());
                    } else {
                        String execPath = resolveExecutablePath();
                        configurator.configure(execPath);
                        messages.add("✅ %s — MCP server configured".formatted(configurator.getClientName()));
                        configuredClients.add(configurator.getClientName());
                    }
                } else {
                    messages.add("⏭️ %s — not installed, skipping".formatted(configurator.getClientName()));
                }
            } catch (Exception e) {
                messages.add("⚠️ %s — configuration failed: %s"
                        .formatted(configurator.getClientName(), e.getMessage()));
                log.warn("Client configurator failed for {}: {}", configurator.getClientName(), e.getMessage());
            }
        }

        return new SetupResult(true, null, configuredDatabases, configuredClients, messages);
    }

    /**
     * Resolves the path to the current DB-Pilot executable/JAR.
     */
    private String resolveExecutablePath() {
        try {
            String path = SetupWizardService.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath();
            // If running from a JAR, return "java -jar <path>"
            if (path.endsWith(".jar")) {
                return "java -jar " + path;
            }
            return path;
        } catch (Exception e) {
            log.warn("Could not resolve executable path: {}", e.getMessage());
            return "db-pilot";
        }
    }

    /**
     * Stores a database connection via the credential store.
     * Called by the CLI setup wizard after interactive prompts.
     */
    public void storeConnection(String alias, DatabaseConnectionInfo connInfo) {
        credentialStore.store(alias, connInfo);
        log.info("Stored connection '{}' in OS Keyring", alias);
    }
}
