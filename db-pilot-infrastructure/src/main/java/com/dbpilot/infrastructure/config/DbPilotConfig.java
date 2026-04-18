package com.dbpilot.infrastructure.config;

import com.dbpilot.core.port.in.HealthCheckUseCase;
import com.dbpilot.core.port.in.SetupWizardUseCase;
import com.dbpilot.core.port.in.TranslateQueryUseCase;
import com.dbpilot.core.port.out.*;
import com.dbpilot.core.service.*;
import com.dbpilot.infrastructure.adapter.configurator.*;
import com.dbpilot.infrastructure.adapter.credential.OsKeyringCredentialStore;
import com.dbpilot.infrastructure.adapter.db.DatabaseAdapterFactory;
import com.dbpilot.infrastructure.adapter.llm.LangChain4jLlmGateway;
import com.dbpilot.infrastructure.adapter.ranking.TripleLayerTableRanker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;

/**
 * Spring configuration for wiring the hexagonal architecture.
 *
 * <p>Since domain/application layers have no Spring annotations,
 * this class manually creates and wires all beans.</p>
 *
 * @author DB-Pilot
 */
@Configuration
@EntityScan(basePackages = "com.dbpilot.infrastructure.persistence.entity")
@EnableJpaRepositories(basePackages = "com.dbpilot.infrastructure.persistence.repository")
public class DbPilotConfig {

    @Value("${dbpilot.llm.provider:anthropic}")
    private String llmProvider;

    @Value("${dbpilot.llm.api-key:}")
    private String llmApiKey;

    @Value("${dbpilot.token-limit:4000}")
    private int tokenLimit;

    // ==================== Domain Services ====================

    @Bean
    public TokenGuard tokenGuard() {
        return new TokenGuard(tokenLimit);
    }

    @Bean
    public SchemaCompactor schemaCompactor() {
        return new SchemaCompactor();
    }

    @Bean
    public PromptFactory promptFactory(TokenGuard tokenGuard) {
        return new PromptFactory(tokenGuard);
    }

    @Bean
    public LearningLoopService learningLoopService(LlmGateway llmGateway,
                                                    KnowledgeStoreAdapter knowledgeStore) {
        return new LearningLoopService(llmGateway, knowledgeStore);
    }

    @Bean
    public TranslateQueryUseCase queryTranslationService(
            DatabaseAdapter databaseAdapter,
            KnowledgeStoreAdapter knowledgeStore,
            TableRanker tableRanker,
            LlmGateway llmGateway,
            PromptFactory promptFactory,
            SchemaCompactor schemaCompactor) {
        return new QueryTranslationService(
                databaseAdapter, knowledgeStore, tableRanker,
                llmGateway, promptFactory, schemaCompactor);
    }

    @Bean
    public HealthCheckUseCase healthCheckService(
            CredentialStore credentialStore,
            KnowledgeStoreAdapter knowledgeStore,
            LlmGateway llmGateway) {
        return new HealthCheckService(credentialStore, knowledgeStore, llmGateway);
    }

    @Bean
    public SetupWizardUseCase setupWizardService(
            CredentialStore credentialStore,
            List<ClientConfigurator> clientConfigurators) {
        return new SetupWizardService(credentialStore, clientConfigurators);
    }

    // ==================== Infrastructure Adapters ====================

    @Bean
    public DatabaseAdapterFactory databaseAdapterFactory() {
        return new DatabaseAdapterFactory();
    }

    /**
     * Default DatabaseAdapter — routes requests to the correct engine
     * based on the database type from the connection info.
     */
    @Bean
    public DatabaseAdapter defaultDatabaseAdapter(DatabaseAdapterFactory factory) {
        return new com.dbpilot.infrastructure.adapter.db.RoutingDatabaseAdapter(factory);
    }

    @Bean
    public TableRanker tableRanker() {
        return new TripleLayerTableRanker();
    }

    @Bean
    public CredentialStore credentialStore() {
        return new OsKeyringCredentialStore();
    }

    @Bean
    public LlmGateway llmGateway() {
        if (llmApiKey == null || llmApiKey.isBlank()) {
            // Return a no-op gateway for setup mode
            return new LangChain4jLlmGateway(null, "Not configured — run db-pilot setup");
        }
        return switch (llmProvider.toLowerCase()) {
            case "anthropic" -> LangChain4jLlmGateway.anthropicDefault(llmApiKey);
            default -> LangChain4jLlmGateway.anthropicDefault(llmApiKey);
        };
    }

    // ==================== Client Configurators ====================

    @Bean
    public List<ClientConfigurator> clientConfigurators() {
        return List.of(
                new ClaudeDesktopConfigurator(),
                new CursorConfigurator(),
                new VsCodeConfigurator()
        );
    }
}
