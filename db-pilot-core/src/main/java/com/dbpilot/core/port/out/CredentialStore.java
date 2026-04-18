package com.dbpilot.core.port.out;

import com.dbpilot.core.model.DatabaseConnectionInfo;

/**
 * Outbound port for secure credential storage.
 *
 * <p>Implementations must use the OS-native credential manager:</p>
 * <ul>
 *   <li><strong>Windows:</strong> Credential Manager</li>
 *   <li><strong>macOS:</strong> Keychain</li>
 *   <li><strong>Linux:</strong> GNOME Keyring / libsecret (Secret Service API)</li>
 * </ul>
 *
 * <p>Credentials are NEVER stored in plain text files.</p>
 *
 * @author DB-Pilot
 */
public interface CredentialStore {

    /**
     * Stores database credentials securely in the OS keyring.
     *
     * @param alias    unique alias for this connection (e.g., "prod-postgres")
     * @param connInfo the connection info containing credentials to store
     */
    void store(String alias, DatabaseConnectionInfo connInfo);

    /**
     * Retrieves a stored connection from the OS keyring.
     *
     * @param alias the connection alias
     * @return the full connection info, or {@code null} if not found
     */
    DatabaseConnectionInfo retrieve(String alias);

    /**
     * Deletes a stored credential.
     *
     * @param alias the connection alias to remove
     * @return {@code true} if the credential was found and deleted
     */
    boolean delete(String alias);

    /**
     * Lists all stored connection aliases.
     *
     * @return list of alias names
     */
    java.util.List<String> listAliases();

    /**
     * Checks whether the OS keyring is available and functional.
     *
     * @return {@code true} if the keyring is accessible
     */
    boolean isAvailable();
}
