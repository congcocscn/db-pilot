# DB-Pilot Module Reference

Complete inventory of every class in the project, organized by module.

---

## 🔵 db-pilot-core (19 classes)

### Models (`com.dbpilot.core.model`)

| Class | Type | Fields | Key Methods |
|---|---|---|---|
| `DatabaseConnectionInfo` | `@Value @Builder` | alias, databaseType, host, port, databaseName, schema, username, password, jdbcUrl | `resolveJdbcUrl()` — builds JDBC URL from components |
| `DatabaseType` | `enum` | ORACLE, POSTGRESQL, MYSQL, MARIADB, MONGODB | `fromString(String)` — case-insensitive parsing with aliases ("pg", "mongo") |
| `TableMetadata` | `@Value @Builder` | tableName, schemaName, description, estimatedRowCount, columns, foreignKeys, referencedBy | `toCompactYaml()` — token-efficient YAML format |
| `ColumnMetadata` | `@Value @Builder` | name, dataType, nullable, primaryKey, description, defaultValue | `toCompactString()` — "name:type[:PK][:NULL]" |
| `ForeignKeyRelation` | `@Value @Builder` | constraintName, sourceTable, sourceColumn, targetTable, targetColumn | — |
| `RankedTable` | `@Value @Builder` | table, score, semanticScore, relationScore, frequencyScore, rankingReason | Combined score = α×sem + β×rel + γ×freq |
| `BusinessRule` | `@Value @Builder` | id, ruleText, scope (GLOBAL/USER), userId, databaseAlias, createdAt, applicationCount, confidence | Has inner `Scope` enum |
| `UserHabit` | `@Value @Builder` | id, userId, databaseAlias, tableName, queryCount, queryPattern, lastUsedAt, firstUsedAt | — |
| `QueryResult` | `@Value @Builder` | query, columnNames, rows, rowCount, affectedRows, executionTime, dryRun, explainPlan, warnings, errorMessage | `isSuccess()`, `toSummary()` |
| `CompactSchema` | `@Value @Builder` | databaseAlias, databaseType, compactTableEntries, estimatedTokenCount | `toYamlBlock()` — joins entries |

### Inbound Ports (`com.dbpilot.core.port.in`)

| Interface | Command Record | Response Record | Method |
|---|---|---|---|
| `TranslateQueryUseCase` | `TranslateCommand(userId, databaseAlias, naturalQuery, execute, dryRun)` | `TranslateResponse(generatedQuery, queryResult, explanation)` | `translate(cmd)` |
| `LearnFromCorrectionUseCase` | `CorrectionCommand(userId, databaseAlias, originalQuery, correctedQuery, schemaContext)` | `CorrectionResponse(extractedRule, saved, message)` | `learn(cmd)` |
| `SetupWizardUseCase` | `SetupCommand(userId, skipClientConfig)` | `SetupResult(success, configuredDatabases, configuredClients, messages)` | `runSetup(cmd)` |
| `HealthCheckUseCase` | — | `HealthReport(allHealthy, components, summaryMessage)` | `check()` |

### Outbound Ports (`com.dbpilot.core.port.out`)

| Interface | Methods | Implementation |
|---|---|---|
| `DatabaseAdapter` | `introspectSchema`, `executeQuery`, `explainQuery`, `testConnection`, `getDatabaseType` | 5 adapters (PG, Oracle, MySQL, MariaDB, MongoDB) |
| `KnowledgeStoreAdapter` | 12 methods across Global/User/Maintenance scopes | `RelationalKnowledgeStore` (H2/JPA) |
| `TableRanker` | `rank(intent, tables, habits, maxResults)` | `TripleLayerTableRanker` |
| `LlmGateway` | `generateQuery`, `extractBusinessRule`, `embed`, `isAvailable`, `getProviderName` | `LangChain4jLlmGateway` |
| `CredentialStore` | `store`, `retrieve`, `delete`, `listAliases`, `isAvailable` | `OsKeyringCredentialStore` |
| `ClientConfigurator` | `getClientName`, `isInstalled`, `isAlreadyConfigured`, `configure`, `unconfigure` | 3 configurators |

### Domain Services (`com.dbpilot.core.service`)

| Class | Dependencies | Responsibility |
|---|---|---|
| `QueryTranslationService` | DatabaseAdapter, KnowledgeStoreAdapter, TableRanker, LlmGateway, PromptFactory, SchemaCompactor | Full 9-step pipeline orchestrator. Implements `TranslateQueryUseCase`. |
| `PromptFactory` | TokenGuard | Assembles 4-layer system prompt with DB-specific instructions. |
| `TokenGuard` | — | 4000-token budget. Progressive schema pruning when exceeded. |
| `SchemaCompactor` | — | Strips audit columns, normalizes to compact YAML. |
| `LearningLoopService` | LlmGateway, KnowledgeStoreAdapter | Correction → business rule extraction and persistence. |

---

## 🔴 db-pilot-infrastructure (21 classes)

### Database Adapters (`com.dbpilot.infrastructure.adapter.db`)

| Class | Engine | Schema Source | EXPLAIN Syntax | Key Error Codes |
|---|---|---|---|---|
| `AbstractJdbcDatabaseAdapter` | Base | — | — | Generic JDBC |
| `PostgresDatabaseAdapter` | PostgreSQL | `information_schema` + `pg_class` rows | `EXPLAIN (FORMAT TEXT)` | SQLSTATE: 42P01, 42703, 42601, 28P01 |
| `OracleDatabaseAdapter` | Oracle | `ALL_TAB_COLUMNS`, `ALL_CONSTRAINTS` | `DBMS_XPLAN.DISPLAY()` | ORA-942, 904, 900, 1017, 12541 |
| `MySqlDatabaseAdapter` | MySQL | `INFORMATION_SCHEMA` | `EXPLAIN FORMAT=TRADITIONAL` | 1146, 1054, 1064, 1045, 2003 |
| `MariaDbDatabaseAdapter` | MariaDB | Extends MySQL | `EXPLAIN FORMAT=JSON` | Same as MySQL |
| `MongoDatabaseAdapter` | MongoDB | Document sampling (100 docs) | `db.runCommand(explain)` | Native exceptions |
| `DatabaseAdapterFactory` | — | `EnumMap<DatabaseType, DatabaseAdapter>` | — | — |

### Knowledge Store (`com.dbpilot.infrastructure.adapter.knowledge`)

| Class | Description |
|---|---|
| `RelationalKnowledgeStore` | Full `KnowledgeStoreAdapter` implementation using H2/JPA. Handles rules, habits, and schema cache. |

### JPA Entities (`com.dbpilot.infrastructure.persistence.entity`)

| Entity | Table | Key Columns | Indexes |
|---|---|---|---|
| `BusinessRuleEntity` | `business_rules` | id, ruleText, scope, userId, databaseAlias, confidence | scope, userId, databaseAlias |
| `UserHabitEntity` | `user_habits` | id, userId, databaseAlias, tableName, queryCount | userId, userId+databaseAlias, UQ(userId+db+table) |
| `SchemaMetadataEntity` | `schema_metadata` | databaseAlias (PK), schemaJson (CLOB), cachedAt, cacheTtlMinutes | databaseAlias |

### Repositories (`com.dbpilot.infrastructure.persistence.repository`)

| Repository | Key Queries |
|---|---|
| `BusinessRuleRepository` | `findByScopeOrderByCreatedAtDesc`, `findByScopeAndDatabaseAlias`, `findByUserId` |
| `UserHabitRepository` | `findByUserIdOrderByQueryCountDesc`, `findByUserIdAndDatabaseAliasAndTableName` |
| `SchemaMetadataRepository` | Standard CRUD (PK = databaseAlias) |

### Other Adapters

| Class | Port | Key Details |
|---|---|---|
| `LangChain4jLlmGateway` | `LlmGateway` | Claude 4.6 Sonnet, temp=0, auto-strips markdown code fences |
| `TripleLayerTableRanker` | `TableRanker` | Weights: α=0.5, β=0.3, γ=0.2. Keyword-based (upgradeable to embedding). |
| `EmbeddingService` | — | 3 modes: ONNX BGE-small, LLM API, keyword tri-gram fallback |
| `OsKeyringCredentialStore` | `CredentialStore` | Stores as JSON blob. Index at `__aliases__` key. |
| `ClaudeDesktopConfigurator` | `ClientConfigurator` | Path: `%APPDATA%/Claude/claude_desktop_config.json` |
| `CursorConfigurator` | `ClientConfigurator` | Path: `~/.cursor/mcp.json` |
| `VsCodeConfigurator` | `ClientConfigurator` | Path: `%APPDATA%/Code/User/settings.json` → `mcp.servers` |
| `DatabaseErrorMapper` | — | Static maps: Oracle(ORA→hint), PG(SQLSTATE→hint), MySQL(code→hint) |
| `DbPilotConfig` | — | Spring `@Configuration` manually wiring all domain services |

---

## 🟠 db-pilot-mcp (4 classes)

| Class | MCP Tool Name | Description |
|---|---|---|
| `QueryTool` | `translate_query` | NL → SQL with markdown table formatting |
| `SchemaExplorerTool` | `explore_schema` | Tables/columns/FKs browser |
| `HealthTool` | `health_check` | System health report |
| `McpServerConfig` | — | STDIO JSON-RPC loop. Routes `initialize`, `tools/list`, `tools/call` |

---

## 🟣 db-pilot-app (5 classes + 1 config)

| Class | Type | Description |
|---|---|---|
| `DbPilotApplication` | Spring Boot main | Entry point. Redirects banner to stderr for MCP. |
| `DbPilotCli` | Picocli root | Routes to subcommands. Shows banner on no-args. |
| `SetupCommand` | Picocli subcommand | Interactive wizard: DB config → test → keyring → client auto-config |
| `CheckCommand` | Picocli subcommand | Health check with emoji status indicators |
| `DryRunCommand` | Picocli subcommand | Generate query + EXPLAIN without executing |
| `application.yml` | Config | Virtual threads, H2 datasource, LLM config, MDC logging |
