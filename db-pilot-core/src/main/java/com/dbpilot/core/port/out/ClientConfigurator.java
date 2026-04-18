package com.dbpilot.core.port.out;

/**
 * Outbound port for auto-configuring AI client applications.
 *
 * <p>Implementations detect installed AI clients and inject the DB-Pilot
 * MCP server configuration into their config files:</p>
 * <ul>
 *   <li><strong>Claude Desktop:</strong> {@code claude_desktop_config.json}</li>
 *   <li><strong>Cursor:</strong> {@code .cursor/mcp.json}</li>
 *   <li><strong>VS Code:</strong> MCP extension settings</li>
 * </ul>
 *
 * @author DB-Pilot
 */
public interface ClientConfigurator {

    /**
     * Returns the human-readable name of the client application.
     *
     * @return client name (e.g., "Claude Desktop", "Cursor")
     */
    String getClientName();

    /**
     * Checks whether this client is installed on the system.
     *
     * @return {@code true} if the client is detected
     */
    boolean isInstalled();

    /**
     * Checks whether DB-Pilot is already registered with this client.
     *
     * @return {@code true} if MCP config already contains db-pilot
     */
    boolean isAlreadyConfigured();

    /**
     * Injects the DB-Pilot MCP server configuration into the client's config file.
     *
     * @param executablePath the absolute path to the db-pilot executable or JAR
     * @return a human-readable result message
     */
    String configure(String executablePath);

    /**
     * Removes the DB-Pilot MCP configuration from the client.
     *
     * @return a human-readable result message
     */
    String unconfigure();
}
