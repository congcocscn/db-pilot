package com.dbpilot.core.service;

import com.dbpilot.core.model.*;
import com.dbpilot.core.port.in.TranslateQueryUseCase;
import com.dbpilot.core.port.out.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Application service orchestrating the complete query translation pipeline.
 *
 * <p>This is the central use case implementation that ties together all domain
 * components: schema loading, table ranking, prompt assembly, token management,
 * LLM invocation, and optional query execution.</p>
 *
 * @author DB-Pilot
 */
@Slf4j
@RequiredArgsConstructor
public class QueryTranslationService implements TranslateQueryUseCase {

    private final DatabaseAdapter databaseAdapter;
    private final KnowledgeStoreAdapter knowledgeStore;
    private final TableRanker tableRanker;
    private final LlmGateway llmGateway;
    private final PromptFactory promptFactory;
    private final SchemaCompactor schemaCompactor;

    /** Maximum number of tables to include in the prompt context. */
    private static final int MAX_RANKED_TABLES = 15;

    @Override
    public TranslateResponse translate(TranslateCommand command) {
        log.info("Translating query for user '{}' on database '{}': {}",
                command.userId(), command.databaseAlias(), command.naturalQuery());

        // 1. Resolve connection info
        var connectionInfo = resolveConnection(command.databaseAlias());

        // 2. Load schema metadata (from cache or live introspection)
        List<TableMetadata> allTables = loadSchema(command.databaseAlias(), connectionInfo);

        // 3. Load user habits for frequency weighting
        List<UserHabit> userHabits = knowledgeStore.getUserHabits(
                command.userId(), command.databaseAlias());

        // 4. Rank tables by relevance using triple-layer algorithm
        List<RankedTable> rankedTables = tableRanker.rank(
                command.naturalQuery(), allTables, userHabits, MAX_RANKED_TABLES);
        log.debug("Ranked {} tables (top: {})", rankedTables.size(),
                rankedTables.isEmpty() ? "none" : rankedTables.get(0).getTable().getTableName());

        // 5. Compact schema to token-efficient YAML
        CompactSchema compactSchema = schemaCompactor.compact(
                rankedTables, command.databaseAlias(), connectionInfo.getDatabaseType());

        // 6. Load business rules
        List<BusinessRule> globalRules = knowledgeStore.getGlobalRules(command.databaseAlias());
        List<BusinessRule> userRules = knowledgeStore.getUserRules(command.userId());

        // 7. Assemble prompt (with token budget enforcement)
        String systemPrompt = promptFactory.assembleSystemPrompt(
                connectionInfo.getDatabaseType(), compactSchema,
                globalRules, userRules, userHabits);

        // 8. Generate query via LLM
        String generatedQuery = llmGateway.generateQuery(systemPrompt, command.naturalQuery());
        log.info("Generated query: {}", generatedQuery);

        // 9. Record table access for habit learning
        rankedTables.stream()
                .filter(rt -> rt.getScore() > 0.3) // Only track meaningfully ranked tables
                .forEach(rt -> knowledgeStore.recordTableAccess(
                        command.userId(), command.databaseAlias(),
                        rt.getTable().getTableName()));

        // 10. Execute or explain if requested
        QueryResult queryResult = null;
        if (command.dryRun()) {
            queryResult = databaseAdapter.explainQuery(connectionInfo, generatedQuery);
        } else if (command.execute()) {
            queryResult = databaseAdapter.executeQuery(connectionInfo, generatedQuery);
        }

        return new TranslateResponse(
                generatedQuery,
                queryResult,
                buildExplanation(rankedTables, compactSchema)
        );
    }

    /**
     * Loads schema from cache or performs live introspection.
     */
    private List<TableMetadata> loadSchema(String databaseAlias,
                                            DatabaseConnectionInfo connectionInfo) {
        if (knowledgeStore.isSchemaCached(databaseAlias)) {
            log.debug("Using cached schema for '{}'", databaseAlias);
            return knowledgeStore.getCachedSchema(databaseAlias);
        }

        log.info("Introspecting schema for '{}'...", databaseAlias);
        List<TableMetadata> tables = databaseAdapter.introspectSchema(connectionInfo);
        knowledgeStore.cacheSchema(databaseAlias, tables);
        log.info("Cached {} tables for '{}'", tables.size(), databaseAlias);
        return tables;
    }

    /**
     * Resolves connection info — placeholder for credential store integration.
     * This will be wired to the CredentialStore in the infrastructure layer.
     */
    private DatabaseConnectionInfo resolveConnection(String databaseAlias) {
        // TODO: Wire to CredentialStore.retrieve(alias) in infrastructure config
        throw new UnsupportedOperationException(
                "Connection resolution will be wired via Spring DI in the infrastructure layer");
    }

    /**
     * Builds a human-readable explanation of the query generation process.
     */
    private String buildExplanation(List<RankedTable> rankedTables, CompactSchema schema) {
        var sb = new StringBuilder();
        sb.append("📊 Used %d tables in context (%d tokens)\n".formatted(
                rankedTables.size(), schema.getEstimatedTokenCount()));
        sb.append("Top tables: ");
        rankedTables.stream()
                .limit(3)
                .forEach(rt -> sb.append("%s (%.2f) ".formatted(
                        rt.getTable().getTableName(), rt.getScore())));
        return sb.toString();
    }
}
