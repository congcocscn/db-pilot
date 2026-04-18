package com.dbpilot.infrastructure.error;

import com.dbpilot.core.model.DatabaseType;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.Map;

/**
 * Centralized database error mapper that translates vendor-specific
 * error codes into human-readable hints suitable for AI consumption.
 *
 * <p>Each database engine uses different error code systems:</p>
 * <ul>
 *   <li><strong>Oracle:</strong> ORA-XXXXX numeric codes</li>
 *   <li><strong>PostgreSQL:</strong> 5-char SQLSTATE codes</li>
 *   <li><strong>MySQL/MariaDB:</strong> Numeric error codes</li>
 *   <li><strong>MongoDB:</strong> Numeric error codes</li>
 * </ul>
 *
 * @author DB-Pilot
 */
@Slf4j
public class DatabaseErrorMapper {

    // ==================== Oracle Error Codes ====================
    private static final Map<Integer, String> ORACLE_ERRORS = Map.ofEntries(
            Map.entry(942, "Table or view does not exist. Check table name and schema access."),
            Map.entry(904, "Invalid column identifier. Verify column name spelling."),
            Map.entry(900, "Invalid SQL statement. Check query syntax."),
            Map.entry(923, "FROM keyword not found where expected. Check SQL syntax."),
            Map.entry(936, "Missing expression in SQL statement."),
            Map.entry(1, "Unique constraint violated. Duplicate key value."),
            Map.entry(1017, "Invalid username/password. Check credentials."),
            Map.entry(1400, "Cannot insert NULL into a NOT NULL column."),
            Map.entry(1722, "Invalid number. Check data type conversions."),
            Map.entry(2291, "Foreign key constraint violated. Parent key not found."),
            Map.entry(2292, "Cannot delete — child records exist."),
            Map.entry(12154, "TNS: could not resolve connect identifier. Check service name."),
            Map.entry(12541, "TNS: no listener. Check if Oracle is running.")
    );

    // ==================== PostgreSQL SQLSTATE Codes ====================
    private static final Map<String, String> POSTGRES_ERRORS = Map.ofEntries(
            Map.entry("42P01", "Table does not exist. Check the table name and schema."),
            Map.entry("42703", "Column does not exist. Verify column name spelling."),
            Map.entry("42601", "SQL syntax error. Check query syntax."),
            Map.entry("42501", "Insufficient privilege. Check user permissions."),
            Map.entry("23505", "Unique constraint violated. Duplicate key value."),
            Map.entry("23503", "Foreign key constraint violated."),
            Map.entry("23502", "Not-null constraint violated."),
            Map.entry("28P01", "Authentication failed. Check username and password."),
            Map.entry("3D000", "Database does not exist. Verify database name."),
            Map.entry("08001", "Connection refused. Check if PostgreSQL is running."),
            Map.entry("08006", "Connection failure. Server may have terminated abnormally."),
            Map.entry("22P02", "Invalid text representation. Check data type conversions.")
    );

    // ==================== MySQL/MariaDB Error Codes ====================
    private static final Map<Integer, String> MYSQL_ERRORS = Map.ofEntries(
            Map.entry(1045, "Access denied. Check username and password."),
            Map.entry(1049, "Unknown database. Verify database name."),
            Map.entry(1054, "Unknown column. Check column name spelling."),
            Map.entry(1062, "Duplicate entry for unique key."),
            Map.entry(1064, "SQL syntax error. Check query syntax."),
            Map.entry(1146, "Table doesn't exist. Verify database and table name."),
            Map.entry(1216, "Foreign key constraint fails — child row exists."),
            Map.entry(1217, "Foreign key constraint fails — cannot delete parent row."),
            Map.entry(1452, "Foreign key constraint fails — referenced value not found."),
            Map.entry(2003, "Cannot connect to MySQL. Check if the server is running."),
            Map.entry(2006, "MySQL server has gone away. Connection lost."),
            Map.entry(2013, "Lost connection during query. Server may be overloaded.")
    );

    /**
     * Maps a SQLException to a human-readable error message.
     *
     * @param dbType the database type
     * @param e      the SQL exception
     * @return a structured, human-readable error message
     */
    public static String map(DatabaseType dbType, SQLException e) {
        String hint = switch (dbType) {
            case ORACLE -> ORACLE_ERRORS.getOrDefault(e.getErrorCode(), e.getMessage());
            case POSTGRESQL -> POSTGRES_ERRORS.getOrDefault(e.getSQLState(), e.getMessage());
            case MYSQL, MARIADB -> MYSQL_ERRORS.getOrDefault(e.getErrorCode(), e.getMessage());
            case MONGODB -> e.getMessage(); // MongoDB doesn't use SQLException
        };

        String prefix = switch (dbType) {
            case ORACLE -> "ORA-%05d".formatted(e.getErrorCode());
            case POSTGRESQL -> "SQLSTATE %s".formatted(e.getSQLState());
            case MYSQL -> "MySQL Error %d".formatted(e.getErrorCode());
            case MARIADB -> "MariaDB Error %d".formatted(e.getErrorCode());
            case MONGODB -> "MongoDB Error";
        };

        return "[%s] %s — %s".formatted(dbType.getDisplayName(), prefix, hint);
    }

    /**
     * Maps a generic exception (e.g., MongoDB driver) to a human-readable message.
     *
     * @param dbType  the database type
     * @param message the error message
     * @param code    the error code (-1 if unknown)
     * @return formatted error message
     */
    public static String map(DatabaseType dbType, String message, int code) {
        return "[%s] Error %d — %s".formatted(dbType.getDisplayName(), code, message);
    }
}
