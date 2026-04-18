package com.dbpilot.infrastructure.adapter.configurator;

import com.dbpilot.core.port.out.ClientConfigurator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@link ClientConfigurator} for Claude Desktop.
 *
 * <p>Auto-detects Claude Desktop installation and injects DB-Pilot
 * MCP server configuration into {@code claude_desktop_config.json}.</p>
 *
 * <p>Config locations:</p>
 * <ul>
 *   <li><strong>Windows:</strong> {@code %APPDATA%\Claude\claude_desktop_config.json}</li>
 *   <li><strong>macOS:</strong> {@code ~/Library/Application Support/Claude/claude_desktop_config.json}</li>
 *   <li><strong>Linux:</strong> {@code ~/.config/Claude/claude_desktop_config.json}</li>
 * </ul>
 *
 * @author DB-Pilot
 */
@Slf4j
public class ClaudeDesktopConfigurator implements ClientConfigurator {

    private static final String MCP_KEY = "db-pilot";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getClientName() {
        return "Claude Desktop";
    }

    @Override
    public boolean isInstalled() {
        return Files.exists(getConfigDir());
    }

    @Override
    public boolean isAlreadyConfigured() {
        Path configFile = getConfigFile();
        if (!Files.exists(configFile)) return false;
        try {
            JsonNode root = objectMapper.readTree(configFile.toFile());
            JsonNode servers = root.path("mcpServers");
            return servers.has(MCP_KEY);
        } catch (IOException e) {
            log.warn("Could not read Claude Desktop config: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String configure(String executablePath) {
        Path configFile = getConfigFile();
        try {
            ObjectNode root;
            if (Files.exists(configFile)) {
                root = (ObjectNode) objectMapper.readTree(configFile.toFile());
            } else {
                root = objectMapper.createObjectNode();
                Files.createDirectories(configFile.getParent());
            }

            // Create or update mcpServers section
            ObjectNode mcpServers = root.has("mcpServers")
                    ? (ObjectNode) root.get("mcpServers")
                    : root.putObject("mcpServers");

            // Add db-pilot entry
            ObjectNode dbPilot = mcpServers.putObject(MCP_KEY);
            dbPilot.put("command", executablePath);
            dbPilot.putArray("args").add("--mcp");

            // Write back
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), root);

            log.info("✅ Claude Desktop configured: {}", configFile);
            return "✅ Claude Desktop configured at " + configFile;

        } catch (IOException e) {
            String msg = "❌ Failed to configure Claude Desktop: " + e.getMessage();
            log.error(msg);
            return msg;
        }
    }

    @Override
    public String unconfigure() {
        Path configFile = getConfigFile();
        if (!Files.exists(configFile)) return "No Claude Desktop config found";
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(configFile.toFile());
            JsonNode servers = root.path("mcpServers");
            if (servers instanceof ObjectNode serversObj && serversObj.has(MCP_KEY)) {
                serversObj.remove(MCP_KEY);
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), root);
                return "✅ DB-Pilot removed from Claude Desktop config";
            }
            return "DB-Pilot was not configured in Claude Desktop";
        } catch (IOException e) {
            return "❌ Failed to unconfigure: " + e.getMessage();
        }
    }

    private Path getConfigDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return Path.of(System.getenv("APPDATA"), "Claude");
        } else if (os.contains("mac")) {
            return Path.of(System.getProperty("user.home"), "Library", "Application Support", "Claude");
        } else {
            return Path.of(System.getProperty("user.home"), ".config", "Claude");
        }
    }

    private Path getConfigFile() {
        return getConfigDir().resolve("claude_desktop_config.json");
    }
}
