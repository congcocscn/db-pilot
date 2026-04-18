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
 * {@link ClientConfigurator} for Cursor IDE.
 *
 * <p>Auto-detects Cursor and injects DB-Pilot MCP server config
 * into {@code .cursor/mcp.json} in the user's home directory.</p>
 *
 * @author DB-Pilot
 */
@Slf4j
public class CursorConfigurator implements ClientConfigurator {

    private static final String MCP_KEY = "db-pilot";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getClientName() {
        return "Cursor";
    }

    @Override
    public boolean isInstalled() {
        return Files.exists(getCursorDir());
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

            ObjectNode mcpServers = root.has("mcpServers")
                    ? (ObjectNode) root.get("mcpServers")
                    : root.putObject("mcpServers");

            ObjectNode dbPilot = mcpServers.putObject(MCP_KEY);
            dbPilot.put("command", executablePath);
            dbPilot.putArray("args").add("--mcp");

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), root);
            log.info("✅ Cursor configured: {}", configFile);
            return "✅ Cursor configured at " + configFile;

        } catch (IOException e) {
            String msg = "❌ Failed to configure Cursor: " + e.getMessage();
            log.error(msg);
            return msg;
        }
    }

    @Override
    public String unconfigure() {
        Path configFile = getConfigFile();
        if (!Files.exists(configFile)) return "No Cursor config found";
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(configFile.toFile());
            JsonNode servers = root.path("mcpServers");
            if (servers instanceof ObjectNode s && s.has(MCP_KEY)) {
                s.remove(MCP_KEY);
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), root);
                return "✅ DB-Pilot removed from Cursor config";
            }
            return "DB-Pilot was not configured in Cursor";
        } catch (IOException e) {
            return "❌ Failed to unconfigure: " + e.getMessage();
        }
    }

    private Path getCursorDir() {
        return Path.of(System.getProperty("user.home"), ".cursor");
    }

    private Path getConfigFile() {
        return getCursorDir().resolve("mcp.json");
    }
}
