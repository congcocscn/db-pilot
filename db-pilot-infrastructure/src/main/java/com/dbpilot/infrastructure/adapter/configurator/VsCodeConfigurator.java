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
 * {@link ClientConfigurator} for Visual Studio Code.
 *
 * <p>Auto-detects VS Code and injects DB-Pilot MCP server config
 * into VS Code settings.</p>
 *
 * @author DB-Pilot
 */
@Slf4j
public class VsCodeConfigurator implements ClientConfigurator {

    private static final String MCP_KEY = "db-pilot";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getClientName() {
        return "VS Code";
    }

    @Override
    public boolean isInstalled() {
        return Files.exists(getVsCodeDir());
    }

    @Override
    public boolean isAlreadyConfigured() {
        Path configFile = getSettingsFile();
        if (!Files.exists(configFile)) return false;
        try {
            JsonNode root = objectMapper.readTree(configFile.toFile());
            return root.path("mcp").path("servers").has(MCP_KEY);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String configure(String executablePath) {
        Path configFile = getSettingsFile();
        try {
            ObjectNode root;
            if (Files.exists(configFile)) {
                root = (ObjectNode) objectMapper.readTree(configFile.toFile());
            } else {
                root = objectMapper.createObjectNode();
                Files.createDirectories(configFile.getParent());
            }

            // VS Code MCP uses "mcp.servers" in settings.json
            ObjectNode mcp = root.has("mcp")
                    ? (ObjectNode) root.get("mcp")
                    : root.putObject("mcp");
            ObjectNode servers = mcp.has("servers")
                    ? (ObjectNode) mcp.get("servers")
                    : mcp.putObject("servers");

            ObjectNode dbPilot = servers.putObject(MCP_KEY);
            dbPilot.put("type", "stdio");
            dbPilot.put("command", executablePath);
            dbPilot.putArray("args").add("--mcp");

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), root);
            log.info("✅ VS Code configured: {}", configFile);
            return "✅ VS Code configured at " + configFile;

        } catch (IOException e) {
            String msg = "❌ Failed to configure VS Code: " + e.getMessage();
            log.error(msg);
            return msg;
        }
    }

    @Override
    public String unconfigure() {
        Path configFile = getSettingsFile();
        if (!Files.exists(configFile)) return "No VS Code config found";
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(configFile.toFile());
            JsonNode servers = root.path("mcp").path("servers");
            if (servers instanceof ObjectNode s && s.has(MCP_KEY)) {
                s.remove(MCP_KEY);
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), root);
                return "✅ DB-Pilot removed from VS Code config";
            }
            return "DB-Pilot was not configured in VS Code";
        } catch (IOException e) {
            return "❌ Failed to unconfigure: " + e.getMessage();
        }
    }

    private Path getVsCodeDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return Path.of(System.getenv("APPDATA"), "Code");
        } else if (os.contains("mac")) {
            return Path.of(System.getProperty("user.home"), "Library", "Application Support", "Code");
        } else {
            return Path.of(System.getProperty("user.home"), ".config", "Code");
        }
    }

    private Path getSettingsFile() {
        return getVsCodeDir().resolve("User").resolve("settings.json");
    }
}
