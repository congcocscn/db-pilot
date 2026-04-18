package com.dbpilot.core.port.in;

import java.util.List;

/**
 * Inbound port for the interactive setup wizard ({@code db-pilot --setup}).
 *
 * <p>Orchestrates the first-time configuration flow:</p>
 * <ol>
 *   <li>Capture userId and database credentials</li>
 *   <li>Store credentials in OS Keyring</li>
 *   <li>Test database connectivity</li>
 *   <li>Introspect and cache schema</li>
 *   <li>Auto-detect and configure AI clients (Claude Desktop, Cursor, VS Code)</li>
 * </ol>
 *
 * @author DB-Pilot
 */
public interface SetupWizardUseCase {

    /**
     * Result of the setup wizard.
     *
     * @param success              whether setup completed successfully
     * @param userId               the configured user ID
     * @param configuredDatabases  list of database aliases that were configured
     * @param configuredClients    list of AI clients that were auto-configured
     * @param messages             log of setup actions taken
     */
    record SetupResult(
            boolean success,
            String userId,
            List<String> configuredDatabases,
            List<String> configuredClients,
            List<String> messages
    ) {}

    /**
     * Runs the interactive setup wizard.
     *
     * @return the result of the setup process
     */
    SetupResult runSetup();
}
