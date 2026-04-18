package com.dbpilot.infrastructure.adapter.db;

import com.dbpilot.core.model.*;
import com.dbpilot.core.port.out.DatabaseAdapter;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link DatabaseAdapter} implementation for MongoDB.
 *
 * <p>Unlike JDBC adapters, this uses the MongoDB Java Driver directly.
 * Key differences:</p>
 * <ul>
 *   <li>Schema introspection by sampling documents (MongoDB is schema-less)</li>
 *   <li>Query execution via aggregation pipeline (JSON arrays)</li>
 *   <li>No EXPLAIN support — uses {@code explain} command on aggregation</li>
 * </ul>
 *
 * @author DB-Pilot
 */
@Slf4j
public class MongoDatabaseAdapter implements DatabaseAdapter {

    /** Client cache: alias → MongoClient. */
    private final ConcurrentHashMap<String, MongoClient> clientCache = new ConcurrentHashMap<>();

    /** Maximum documents to sample for schema inference. */
    private static final int SAMPLE_SIZE = 100;

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.MONGODB;
    }

    @Override
    public List<TableMetadata> introspectSchema(DatabaseConnectionInfo connectionInfo) {
        MongoDatabase db = getDatabase(connectionInfo);
        List<TableMetadata> tables = new ArrayList<>();

        for (String collectionName : db.listCollectionNames()) {
            MongoCollection<Document> collection = db.getCollection(collectionName);

            // Sample documents to infer schema
            List<ColumnMetadata> columns = inferSchemaFromSample(collection);
            long estimatedCount = collection.estimatedDocumentCount();

            tables.add(TableMetadata.builder()
                    .tableName(collectionName)
                    .schemaName(connectionInfo.getDatabaseName())
                    .columns(columns)
                    .foreignKeys(List.of()) // MongoDB has no formal FKs
                    .referencedBy(List.of())
                    .estimatedRowCount(estimatedCount)
                    .build());
        }

        log.info("MongoDB introspection for '{}': found {} collections",
                connectionInfo.getAlias(), tables.size());
        return tables;
    }

    @Override
    public QueryResult executeQuery(DatabaseConnectionInfo connectionInfo, String query) {
        Instant start = Instant.now();
        try {
            MongoDatabase db = getDatabase(connectionInfo);

            // Parse the aggregation pipeline JSON
            List<Document> pipeline = parsePipeline(query);

            // Extract collection name from first $match or use first collection
            String collectionName = extractCollectionName(query, db);
            MongoCollection<Document> collection = db.getCollection(collectionName);

            // Execute aggregation
            List<Map<String, Object>> rows = new ArrayList<>();
            Set<String> allKeys = new LinkedHashSet<>();

            for (Document doc : collection.aggregate(pipeline)) {
                Map<String, Object> row = new LinkedHashMap<>(doc);
                allKeys.addAll(row.keySet());
                rows.add(row);
            }

            Duration elapsed = Duration.between(start, Instant.now());
            log.debug("MongoDB aggregation on '{}' — {} documents in {}",
                    connectionInfo.getAlias(), rows.size(), elapsed);

            return QueryResult.builder()
                    .query(query)
                    .columnNames(new ArrayList<>(allKeys))
                    .rows(rows)
                    .rowCount(rows.size())
                    .executionTime(elapsed)
                    .dryRun(false)
                    .build();

        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            log.error("MongoDB query failed: {}", e.getMessage());
            return QueryResult.builder()
                    .query(query)
                    .columnNames(List.of())
                    .rows(List.of())
                    .executionTime(elapsed)
                    .dryRun(false)
                    .errorMessage("[MongoDB] " + e.getMessage())
                    .build();
        }
    }

    @Override
    public QueryResult explainQuery(DatabaseConnectionInfo connectionInfo, String query) {
        Instant start = Instant.now();
        try {
            MongoDatabase db = getDatabase(connectionInfo);
            String collectionName = extractCollectionName(query, db);
            List<Document> pipeline = parsePipeline(query);

            // Use explain command
            Document explainCmd = new Document("explain",
                    new Document("aggregate", collectionName)
                            .append("pipeline", pipeline)
                            .append("cursor", new Document()));

            Document explainResult = db.runCommand(explainCmd);
            Duration elapsed = Duration.between(start, Instant.now());

            return QueryResult.builder()
                    .query(query)
                    .columnNames(List.of())
                    .rows(List.of())
                    .executionTime(elapsed)
                    .dryRun(true)
                    .explainPlan(explainResult.toJson())
                    .build();

        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            return QueryResult.builder()
                    .query(query)
                    .executionTime(elapsed)
                    .dryRun(true)
                    .errorMessage("[MongoDB] EXPLAIN failed: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public boolean testConnection(DatabaseConnectionInfo connectionInfo) {
        try {
            MongoDatabase db = getDatabase(connectionInfo);
            db.runCommand(new Document("ping", 1));
            return true;
        } catch (Exception e) {
            log.warn("MongoDB connection test failed for '{}': {}",
                    connectionInfo.getAlias(), e.getMessage());
            return false;
        }
    }

    /**
     * Infers column (field) metadata from a sample of documents.
     */
    private List<ColumnMetadata> inferSchemaFromSample(MongoCollection<Document> collection) {
        Map<String, String> fieldTypes = new LinkedHashMap<>();

        for (Document doc : collection.find().limit(SAMPLE_SIZE)) {
            for (Map.Entry<String, Object> entry : doc.entrySet()) {
                String fieldName = entry.getKey();
                String type = entry.getValue() != null
                        ? entry.getValue().getClass().getSimpleName()
                        : "null";
                fieldTypes.putIfAbsent(fieldName, type);
            }
        }

        return fieldTypes.entrySet().stream()
                .map(e -> ColumnMetadata.builder()
                        .name(e.getKey())
                        .dataType(e.getValue())
                        .nullable(true)
                        .primaryKey("_id".equals(e.getKey()))
                        .build())
                .toList();
    }

    /**
     * Parses a JSON aggregation pipeline string into a list of Documents.
     */
    @SuppressWarnings("unchecked")
    private List<Document> parsePipeline(String json) {
        // Handle the pipeline as a JSON array
        List<Document> pipeline = new ArrayList<>();
        List<Object> parsed = Document.parse("{\"p\":" + json + "}").getList("p", Object.class);
        for (Object stage : parsed) {
            if (stage instanceof Document doc) {
                pipeline.add(doc);
            }
        }
        return pipeline;
    }

    /**
     * Extracts collection name from the query context.
     * Falls back to the first collection in the database.
     */
    private String extractCollectionName(String query, MongoDatabase db) {
        // Look for a "collection" hint in the query
        // For now, default to first collection (will be enhanced with MCP tool parameter)
        return db.listCollectionNames().first();
    }

    private MongoDatabase getDatabase(DatabaseConnectionInfo connectionInfo) {
        MongoClient client = clientCache.computeIfAbsent(connectionInfo.getAlias(), alias -> {
            String uri = connectionInfo.resolveJdbcUrl();
            log.info("Creating MongoDB client for '{}'", alias);
            return MongoClients.create(uri);
        });
        return client.getDatabase(connectionInfo.getDatabaseName());
    }

    /**
     * Cleans up all MongoDB clients. Call on application shutdown.
     */
    public void shutdown() {
        clientCache.forEach((alias, client) -> {
            log.info("Closing MongoDB client for '{}'", alias);
            client.close();
        });
        clientCache.clear();
    }
}
