package com.dbpilot.infrastructure.adapter.credential;

import com.dbpilot.core.model.DatabaseConnectionInfo;
import com.dbpilot.core.model.DatabaseType;
import com.dbpilot.core.port.out.CredentialStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javakeyring.Keyring;
import com.github.javakeyring.PasswordAccessException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link CredentialStore} implementation using the OS keyring.
 *
 * <p>Delegates to {@link com.github.javakeyring.Keyring}:</p>
 * <ul>
 *   <li><strong>Windows:</strong> Credential Manager</li>
 *   <li><strong>macOS:</strong> Keychain</li>
 *   <li><strong>Linux:</strong> GNOME Keyring / libsecret</li>
 * </ul>
 *
 * <p>Credential format in keyring: JSON blob containing connection metadata.
 * The service name is {@code db-pilot} and the key is the connection alias.</p>
 *
 * @author DB-Pilot
 */
@Slf4j
public class OsKeyringCredentialStore implements CredentialStore {

    private static final String SERVICE_NAME = "db-pilot";
    private static final String ALIAS_LIST_KEY = "__aliases__";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** In-memory index of known aliases (synced with keyring). */
    private final ConcurrentHashMap<String, Boolean> aliasIndex = new ConcurrentHashMap<>();

    public OsKeyringCredentialStore() {
        // Load alias index from keyring on startup
        loadAliasIndex();
    }

    @Override
    public void store(String alias, DatabaseConnectionInfo connInfo) {
        try {
            Keyring keyring = Keyring.create();
            String json = objectMapper.writeValueAsString(Map.of(
                    "alias", alias,
                    "databaseType", connInfo.getDatabaseType().name(),
                    "host", connInfo.getHost() != null ? connInfo.getHost() : "",
                    "port", connInfo.getPort(),
                    "databaseName", connInfo.getDatabaseName() != null ? connInfo.getDatabaseName() : "",
                    "schema", connInfo.getSchema() != null ? connInfo.getSchema() : "",
                    "username", connInfo.getUsername() != null ? connInfo.getUsername() : "",
                    "password", connInfo.getPassword() != null ? connInfo.getPassword() : "",
                    "jdbcUrl", connInfo.getJdbcUrl() != null ? connInfo.getJdbcUrl() : ""
            ));

            keyring.setPassword(SERVICE_NAME, alias, json);
            aliasIndex.put(alias, true);
            saveAliasIndex();
            log.info("✅ Credentials stored for '{}' in OS keyring", alias);

        } catch (Exception e) {
            log.error("Failed to store credentials for '{}': {}", alias, e.getMessage());
            throw new RuntimeException("Credential storage failed: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public DatabaseConnectionInfo retrieve(String alias) {
        try {
            Keyring keyring = Keyring.create();
            String json = keyring.getPassword(SERVICE_NAME, alias);
            if (json == null) return null;

            Map<String, Object> data = objectMapper.readValue(json, Map.class);

            return DatabaseConnectionInfo.builder()
                    .alias(alias)
                    .databaseType(DatabaseType.valueOf((String) data.get("databaseType")))
                    .host((String) data.get("host"))
                    .port(((Number) data.get("port")).intValue())
                    .databaseName((String) data.get("databaseName"))
                    .schema(emptyToNull((String) data.get("schema")))
                    .username((String) data.get("username"))
                    .password((String) data.get("password"))
                    .jdbcUrl(emptyToNull((String) data.get("jdbcUrl")))
                    .build();

        } catch (PasswordAccessException e) {
            log.debug("No credential found for alias '{}'", alias);
            return null;
        } catch (Exception e) {
            log.error("Failed to retrieve credentials for '{}': {}", alias, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean delete(String alias) {
        try {
            Keyring keyring = Keyring.create();
            keyring.deletePassword(SERVICE_NAME, alias);
            aliasIndex.remove(alias);
            saveAliasIndex();
            log.info("Deleted credentials for '{}'", alias);
            return true;
        } catch (Exception e) {
            log.warn("Failed to delete credentials for '{}': {}", alias, e.getMessage());
            return false;
        }
    }

    @Override
    public List<String> listAliases() {
        return new ArrayList<>(aliasIndex.keySet());
    }

    @Override
    public boolean isAvailable() {
        try {
            Keyring.create();
            return true;
        } catch (Exception e) {
            log.warn("OS keyring is not available: {}", e.getMessage());
            return false;
        }
    }

    private void loadAliasIndex() {
        try {
            Keyring keyring = Keyring.create();
            String aliases = keyring.getPassword(SERVICE_NAME, ALIAS_LIST_KEY);
            if (aliases != null && !aliases.isBlank()) {
                for (String alias : aliases.split(",")) {
                    aliasIndex.put(alias.trim(), true);
                }
            }
        } catch (Exception e) {
            log.debug("No existing alias index found in keyring");
        }
    }

    private void saveAliasIndex() {
        try {
            Keyring keyring = Keyring.create();
            String aliases = String.join(",", aliasIndex.keySet());
            keyring.setPassword(SERVICE_NAME, ALIAS_LIST_KEY, aliases);
        } catch (Exception e) {
            log.warn("Failed to save alias index: {}", e.getMessage());
        }
    }

    private String emptyToNull(String s) {
        return (s != null && !s.isEmpty()) ? s : null;
    }
}
