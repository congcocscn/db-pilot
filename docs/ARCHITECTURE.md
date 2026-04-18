# DB-Pilot Architecture

## Hexagonal Architecture (Ports & Adapters)

DB-Pilot follows strict Hexagonal Architecture to ensure the domain logic is completely decoupled from infrastructure concerns.

### Core Principle

```
"The domain must not know about the outside world."
```

- **Inbound Ports** = Use Cases (what the system CAN do)
- **Outbound Ports** = SPIs (what the system NEEDS from the outside)
- **Adapters** = Implementations (HOW the outside world connects)

### Layer Diagram

```
  ┌─────────────────────────────────────────────────────┐
  │                   DRIVING SIDE                       │
  │    ┌──────────┐  ┌──────────┐  ┌────────────────┐  │
  │    │ MCP STDIO│  │ Picocli  │  │ Future: REST   │  │
  │    │ Server   │  │ CLI      │  │ API            │  │
  │    └────┬─────┘  └────┬─────┘  └──────┬─────────┘  │
  │         │              │               │             │
  │    ┌────▼──────────────▼───────────────▼──────┐     │
  │    │          INBOUND PORTS                    │     │
  │    │  TranslateQueryUseCase                    │     │
  │    │  LearnFromCorrectionUseCase               │     │
  │    │  SetupWizardUseCase                       │     │
  │    │  HealthCheckUseCase                       │     │
  │    └────────────────┬──────────────────────────┘     │
  │                     │                                │
  │    ┌────────────────▼──────────────────────────┐     │
  │    │           DOMAIN CORE                      │     │
  │    │  QueryTranslationService                   │     │
  │    │  PromptFactory                             │     │
  │    │  TokenGuard                                │     │
  │    │  SchemaCompactor                           │     │
  │    │  LearningLoopService                       │     │
  │    │  + 10 Domain Models                        │     │
  │    └────────────────┬──────────────────────────┘     │
  │                     │                                │
  │    ┌────────────────▼──────────────────────────┐     │
  │    │          OUTBOUND PORTS                    │     │
  │    │  DatabaseAdapter                           │     │
  │    │  KnowledgeStoreAdapter                     │     │
  │    │  TableRanker                               │     │
  │    │  LlmGateway                                │     │
  │    │  CredentialStore                            │     │
  │    │  ClientConfigurator                         │     │
  │    └────────────────┬──────────────────────────┘     │
  │                     │                                │
  │    ┌────────────────▼──────────────────────────┐     │
  │    │          ADAPTERS (Infrastructure)         │     │
  │    │  PostgresDatabaseAdapter                   │     │
  │    │  OracleDatabaseAdapter                     │     │
  │    │  LangChain4jLlmGateway                     │     │
  │    │  TripleLayerTableRanker                    │     │
  │    │  RelationalKnowledgeStore                  │     │
  │    │  OsKeyringCredentialStore                  │     │
  │    │  ClaudeDesktop/Cursor/VSCode Configurator  │     │
  │    └───────────────────────────────────────────┘     │
  │                   DRIVEN SIDE                        │
  └─────────────────────────────────────────────────────┘
```

---

## Module Boundaries

### db-pilot-core

**Purpose:** Pure domain logic with zero framework dependencies.

**Rules:**
- ✅ Java records, enums, interfaces, pure POJOs
- ✅ Lombok `@Value`, `@Builder`, `@Slf4j`
- ✅ SLF4J API (logging facade only)
- ❌ No Spring annotations (`@Service`, `@Component`, `@Repository`)
- ❌ No JPA/Hibernate
- ❌ No LangChain4j
- ❌ No database drivers

**Why:** The domain layer is the most stable part of the system. It should never need to change because a framework version was updated.

### db-pilot-infrastructure

**Purpose:** All framework integrations and external system communication.

**Rules:**
- ✅ Spring annotations (`@Configuration`, `@Service`, `@Repository`)
- ✅ JPA entities, Spring Data repositories
- ✅ LangChain4j models
- ✅ JDBC drivers, MongoDB driver
- ✅ HikariCP, java-keyring, ONNX Runtime
- ❌ No MCP protocol handling (that's the MCP module's job)
- ❌ No CLI logic

### db-pilot-mcp

**Purpose:** MCP Server implementation — tool definitions and STDIO transport.

**Dependencies:** core + infrastructure

**Key class:** `McpServerConfig` — reads JSON-RPC from stdin, routes to tools, writes responses to stdout.

### db-pilot-app

**Purpose:** Application assembly point — brings everything together.

**Dependencies:** core + infrastructure + mcp

**Key classes:** Spring Boot entry point, Picocli CLI commands, GraalVM native profile.

---

## Data Flow Patterns

### Command Pattern (Inbound)

All inbound ports use Java `record` for commands and responses:

```java
public interface TranslateQueryUseCase {
    record TranslateCommand(
        String userId,
        String databaseAlias,
        String naturalQuery,
        boolean execute,
        boolean dryRun
    ) {}

    record TranslateResponse(
        String generatedQuery,
        QueryResult queryResult,
        String explanation
    ) {}

    TranslateResponse translate(TranslateCommand command);
}
```

### Strategy Pattern (Database Adapters)

`DatabaseAdapterFactory` uses an `EnumMap<DatabaseType, DatabaseAdapter>` to resolve the correct adapter at runtime:

```java
DatabaseAdapter adapter = factory.getAdapter(DatabaseType.POSTGRESQL);
List<TableMetadata> tables = adapter.introspectSchema(connInfo);
```

### Observer Pattern (Learning Loop)

When a user corrects a query:

```
User Correction
    → LearningLoopService.learnFromCorrection()
        → LlmGateway.extractBusinessRule() 
        → KnowledgeStoreAdapter.saveUserRule() or saveGlobalRule()
```

### Factory Pattern (Prompt Assembly)

`PromptFactory` assembles the final LLM prompt from 4 layers:

```
[DB-Specific Instructions]
  + [Ranked Schema YAML]
  + [Global Rules]
  + [User Rules + Habits]
  → TokenGuard.enforce()
  → Final Prompt
```

---

## Key Interfaces (Contracts)

### DatabaseAdapter (6 methods)

```java
List<TableMetadata> introspectSchema(DatabaseConnectionInfo connInfo);
QueryResult executeQuery(DatabaseConnectionInfo connInfo, String query);
QueryResult explainQuery(DatabaseConnectionInfo connInfo, String query);
boolean testConnection(DatabaseConnectionInfo connInfo);
DatabaseType getDatabaseType();
```

### LlmGateway (5 methods)

```java
String generateQuery(String systemPrompt, String userMessage);
BusinessRule extractBusinessRule(String original, String corrected, String context);
float[] embed(String text);
boolean isAvailable();
String getProviderName();
```

### KnowledgeStoreAdapter (12 methods)

Scoped operations:
- **Global:** `saveGlobalRule`, `getGlobalRules`, `cacheSchema`, `getCachedSchema`, `isSchemaCached`
- **User:** `saveUserHabit`, `getUserHabits`, `recordTableAccess`, `saveUserRule`, `getUserRules`
- **Maintenance:** `healthCheck`

---

## Error Handling Strategy

### Database Errors

Each adapter overrides `mapError(SQLException)` to translate vendor-specific codes:

| Engine | Error Code | Human Hint |
|---|---|---|
| Oracle ORA-00942 | Table missing | "Table or view does not exist. Check table name and schema access." |
| PostgreSQL 42P01 | Table missing | "Table does not exist. Check the table name and schema." |
| MySQL 1146 | Table missing | "Table doesn't exist. Verify database and table name." |

### Centralized Mapper

`DatabaseErrorMapper` provides a static utility for cross-engine error mapping used by the AI to suggest fixes.

---

## Threading Model

DB-Pilot uses Java 21 **Virtual Threads** (configured in `application.yml`):

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

This makes blocking JDBC calls efficient — each query runs on a lightweight virtual thread, allowing thousands of concurrent database operations without thread pool exhaustion.

---

## Persistence Architecture

### Embedded H2 (Knowledge Store)

```
~/.db-pilot/knowledge.mv.db
├── business_rules     (JPA: BusinessRuleEntity)
├── user_habits        (JPA: UserHabitEntity)
└── schema_metadata    (JPA: SchemaMetadataEntity)
```

### Schema Cache TTL

Introspected schemas are cached for 24 hours. On the next request after expiry, the system re-introspects automatically.

### OS Keyring (Credentials)

Credentials are stored under the service name `db-pilot` with the connection alias as the key. An index of all aliases is maintained under the special key `__aliases__`.
