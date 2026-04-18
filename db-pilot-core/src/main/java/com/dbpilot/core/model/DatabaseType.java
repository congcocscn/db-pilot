package com.dbpilot.core.model;

/**
 * Enumeration of supported database engines.
 *
 * <p>Each value maps to a specific {@link com.dbpilot.core.port.out.DatabaseAdapter}
 * implementation in the infrastructure layer.</p>
 *
 * @author DB-Pilot
 */
public enum DatabaseType {

    /** Oracle Database (11g+). */
    ORACLE("Oracle", "oracle"),

    /** PostgreSQL (12+). */
    POSTGRESQL("PostgreSQL", "postgresql"),

    /** MySQL (8.0+). */
    MYSQL("MySQL", "mysql"),

    /** MariaDB (10.6+). */
    MARIADB("MariaDB", "mariadb"),

    /** MongoDB (5.0+). Uses native aggregation pipeline — not JDBC. */
    MONGODB("MongoDB", "mongodb");

    private final String displayName;
    private final String id;

    DatabaseType(String displayName, String id) {
        this.displayName = displayName;
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getId() {
        return id;
    }

    /**
     * Resolves a {@link DatabaseType} from a case-insensitive string.
     *
     * @param value the database type identifier (e.g., "postgres", "oracle")
     * @return the matching enum value
     * @throws IllegalArgumentException if no match is found
     */
    public static DatabaseType fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Database type cannot be null or blank");
        }
        String normalized = value.trim().toLowerCase();
        for (DatabaseType type : values()) {
            if (type.id.equals(normalized) || type.displayName.equalsIgnoreCase(normalized)
                    || type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        // Handle common aliases
        return switch (normalized) {
            case "postgres", "pg" -> POSTGRESQL;
            case "maria", "mariadb" -> MARIADB;
            case "mongo" -> MONGODB;
            default -> throw new IllegalArgumentException(
                    "Unknown database type: '%s'. Supported: oracle, postgresql, mysql, mariadb, mongodb".formatted(value));
        };
    }
}
