# 🛢️ DB-Pilot

**Enterprise-Grade Multi-Database AI Agent**

> Translates natural language into precise database queries via the Model Context Protocol (MCP).
> Powered by **Anthropic Claude 4.6 Sonnet**. Supports Oracle, PostgreSQL, MySQL, MariaDB, and MongoDB.

---

## ⚡ Quick Start

```bash
# 1. Build the project
mvn clean package -DskipTests

# 2. Run the interactive setup wizard
java -jar db-pilot-app/target/db-pilot-app-1.0.0-SNAPSHOT.jar setup

# 3. Verify system health
java -jar db-pilot-app/target/db-pilot-app-1.0.0-SNAPSHOT.jar check

# 4. Start as MCP server (for Claude Desktop / Cursor / VS Code)
java -jar db-pilot-app/target/db-pilot-app-1.0.0-SNAPSHOT.jar --dbpilot.mcp.enabled=true
```

---

## 📖 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Modules In Detail](#modules-in-detail)
- [Query Pipeline](#query-pipeline)
- [MCP Tools API](#mcp-tools-api)
- [CLI Commands](#cli-commands)
- [Configuration](#configuration)
- [Security](#security)
- [Key Design Decisions](#key-design-decisions)
- [Technology Stack](#technology-stack)
- [Documentation Index](#documentation-index)

---

## Overview

DB-Pilot is an AI-powered database agent that:

1. **Accepts natural language** — "Show me all orders from last month with total > $1000"
2. **Introspects your database schema** — discovers tables, columns, foreign keys
3. **Ranks relevant tables** — using a triple-layer algorithm (semantic + FK relations + user frequency)
4. **Generates the exact query** — SQL for relational DBs, aggregation pipelines for MongoDB
5. **Learns from corrections** — extracts reusable business rules when you fix a query
6. **Works with AI IDEs** — integrates via MCP with Claude Desktop, Cursor, and VS Code

### What Makes DB-Pilot Different

| Feature | DB-Pilot | Others |
|---|---|---|
| **Multi-DB** | Oracle, PostgreSQL, MySQL, MariaDB, MongoDB | Usually 1-2 engines |
| **Learning** | Atomic learning loop extracts business rules | Static prompt templates |
| **Token Efficiency** | YAML-compact schema + audit column stripping | Full DDL dumps |
| **Security** | OS Keyring (never plain text) | `.env` files |
| **Integration** | Auto-configures Claude/Cursor/VS Code | Manual JSON editing |

---

## Architecture

DB-Pilot uses **Hexagonal Architecture** (Ports & Adapters) with 4 Maven modules:

```
┌─────────────────────── db-pilot-app ───────────────────────┐
│  Spring Boot Entry │ Picocli CLI │ GraalVM Native Profile  │
└────────────┬───────────────────────────────┬────────────────┘
             │                               │
┌────────────▼─────────┐     ┌──────────────▼────────────────┐
│    db-pilot-mcp      │     │   db-pilot-infrastructure     │
│  MCP STDIO Server    │     │   Adapters (DB, LLM, KS,      │
│  3 Tools (Query,     │     │   Credential, Configurator)   │
│  Schema, Health)     │     │   JPA Entities & Repositories │
└────────────┬─────────┘     └──────────────┬────────────────┘
             │                               │
             └──────────────┬────────────────┘
                            │
             ┌──────────────▼────────────────┐
             │       db-pilot-core           │
             │   Pure Domain (Zero Deps)     │
             │   Models │ Ports │ Services   │
             └───────────────────────────────┘
```

### Dependency Rule (Strict)

```
app → mcp → infrastructure → core
              ↓
         core has ZERO framework dependencies (only Lombok + SLF4J API)
```

---

## Project Structure

```
db-pilot/
├── pom.xml                          # Parent POM (Spring Boot 3.4.5)
│
├── db-pilot-core/                   # 🔵 Pure Domain Layer
│   └── src/main/java/com/dbpilot/core/
│       ├── model/                   # 10 domain models
│       │   ├── DatabaseConnectionInfo.java   # Connection VO with JDBC URL resolution
│       │   ├── DatabaseType.java             # Enum: Oracle, PG, MySQL, MariaDB, Mongo
│       │   ├── TableMetadata.java            # Table entity + compact YAML serialization
│       │   ├── ColumnMetadata.java           # Column VO with type simplification
│       │   ├── ForeignKeyRelation.java       # FK relationship VO
│       │   ├── RankedTable.java              # Ranked table with triple-score breakdown
│       │   ├── BusinessRule.java             # Learned rule (GLOBAL/USER scope)
│       │   ├── UserHabit.java                # Per-user table query frequency
│       │   ├── QueryResult.java              # Execution result + explain plan
│       │   └── CompactSchema.java            # Token-efficient YAML schema VO
│       │
│       ├── port/
│       │   ├── in/                  # 4 inbound ports (use cases)
│       │   │   ├── TranslateQueryUseCase.java
│       │   │   ├── LearnFromCorrectionUseCase.java
│       │   │   ├── SetupWizardUseCase.java
│       │   │   └── HealthCheckUseCase.java
│       │   │
│       │   └── out/                 # 6 outbound ports (SPIs)
│       │       ├── DatabaseAdapter.java      # Schema introspection + query execution
│       │       ├── KnowledgeStoreAdapter.java # Business rules + habits + schema cache
│       │       ├── TableRanker.java          # Triple-layer ranking interface
│       │       ├── LlmGateway.java           # Provider-agnostic LLM operations
│       │       ├── CredentialStore.java       # OS keyring storage
│       │       └── ClientConfigurator.java    # MCP client auto-configuration
│       │
│       └── service/                 # 5 domain services
│           ├── QueryTranslationService.java  # Full pipeline orchestrator
│           ├── PromptFactory.java            # 4-layer prompt assembly
│           ├── TokenGuard.java               # 4000-token budget enforcement
│           ├── SchemaCompactor.java          # Audit column stripping + YAML
│           └── LearningLoopService.java      # Correction → business rule
│
├── db-pilot-infrastructure/         # 🔴 Infrastructure Adapters
│   └── src/main/java/com/dbpilot/infrastructure/
│       ├── adapter/
│       │   ├── db/                   # Database adapters
│       │   │   ├── AbstractJdbcDatabaseAdapter.java  # Base: HikariCP + Virtual Threads
│       │   │   ├── PostgresDatabaseAdapter.java      # information_schema
│       │   │   ├── OracleDatabaseAdapter.java        # Data dictionary + DBMS_XPLAN
│       │   │   ├── MySqlDatabaseAdapter.java         # INFORMATION_SCHEMA
│       │   │   ├── MariaDbDatabaseAdapter.java       # Extends MySQL
│       │   │   ├── MongoDatabaseAdapter.java         # Document sampling + pipeline
│       │   │   └── DatabaseAdapterFactory.java       # Strategy map by DatabaseType
│       │   │
│       │   ├── llm/
│       │   │   └── LangChain4jLlmGateway.java       # Claude 4.6 Sonnet (temp=0)
│       │   │
│       │   ├── ranking/
│       │   │   └── TripleLayerTableRanker.java       # α=0.5 sem + β=0.3 FK + γ=0.2 freq
│       │   │
│       │   ├── embedding/
│       │   │   └── EmbeddingService.java             # ONNX + API + keyword fallback
│       │   │
│       │   ├── knowledge/
│       │   │   └── RelationalKnowledgeStore.java     # H2/JPA implementation
│       │   │
│       │   ├── credential/
│       │   │   └── OsKeyringCredentialStore.java     # Win CredMgr / macOS Keychain
│       │   │
│       │   └── configurator/
│       │       ├── ClaudeDesktopConfigurator.java    # claude_desktop_config.json
│       │       ├── CursorConfigurator.java           # .cursor/mcp.json
│       │       └── VsCodeConfigurator.java           # VS Code settings.json
│       │
│       ├── persistence/
│       │   ├── entity/              # JPA entities
│       │   │   ├── BusinessRuleEntity.java
│       │   │   ├── UserHabitEntity.java
│       │   │   └── SchemaMetadataEntity.java
│       │   └── repository/          # Spring Data repositories
│       │       ├── BusinessRuleRepository.java
│       │       ├── UserHabitRepository.java
│       │       └── SchemaMetadataRepository.java
│       │
│       ├── config/
│       │   └── DbPilotConfig.java                    # Spring wiring
│       │
│       └── error/
│           └── DatabaseErrorMapper.java              # ORA/SQLSTATE/MySQL error mapping
│
├── db-pilot-mcp/                    # 🟠 MCP Server
│   └── src/main/java/com/dbpilot/mcp/
│       ├── tool/
│       │   ├── QueryTool.java                # NL → SQL with result formatting
│       │   ├── SchemaExplorerTool.java       # Browse tables/columns/FKs
│       │   └── HealthTool.java               # System health check
│       └── config/
│           └── McpServerConfig.java          # STDIO JSON-RPC loop
│
├── db-pilot-app/                    # 🟣 Application Assembly
│   └── src/main/java/com/dbpilot/app/
│       ├── DbPilotApplication.java           # Spring Boot entry point
│       └── cli/
│           ├── DbPilotCli.java               # Root CLI router
│           ├── SetupCommand.java             # Interactive wizard
│           ├── CheckCommand.java             # Health check
│           └── DryRunCommand.java            # Generate + EXPLAIN
│
└── docs/                            # 📚 Documentation
    ├── ARCHITECTURE.md
    ├── MODULES.md
    ├── CONFIGURATION.md
    └── MCP_API.md
```

---

## Modules In Detail

### 🔵 db-pilot-core (Pure Domain)

**Zero framework dependencies.** Contains only Lombok and SLF4J API.

| Component | Count | Description |
|---|---|---|
| **Models** | 10 | Immutable VOs (`@Value @Builder`). `DatabaseType` enum with 5 engines. |
| **Inbound Ports** | 4 | Use case interfaces with Java records for command/response. |
| **Outbound Ports** | 6 | SPI interfaces — no implementation details leak into core. |
| **Domain Services** | 5 | Pure business logic. Wired via `@Configuration` in infrastructure. |

### 🔴 db-pilot-infrastructure (Adapters)

**Where all frameworks live.** Spring Data JPA, LangChain4j, HikariCP, java-keyring, ONNX.

| Adapter | Implements | Key Feature |
|---|---|---|
| `AbstractJdbcDatabaseAdapter` | `DatabaseAdapter` | HikariCP pool per alias, Virtual Thread friendly |
| `PostgresDatabaseAdapter` | — | `information_schema` + `pg_stat` row estimates |
| `OracleDatabaseAdapter` | — | `ALL_TAB_COLUMNS` + `DBMS_XPLAN` |
| `MySqlDatabaseAdapter` | — | `INFORMATION_SCHEMA` |
| `MariaDbDatabaseAdapter` | — | Extends MySQL, overrides EXPLAIN format |
| `MongoDatabaseAdapter` | `DatabaseAdapter` | Document sampling schema inference, aggregation pipeline |
| `LangChain4jLlmGateway` | `LlmGateway` | Claude 4.6 Sonnet, temp=0, code fence stripping |
| `TripleLayerTableRanker` | `TableRanker` | Composite: α=0.5 semantic + β=0.3 FK + γ=0.2 frequency |
| `EmbeddingService` | — | Dual-mode: ONNX BGE-small + API + keyword fallback |
| `RelationalKnowledgeStore` | `KnowledgeStoreAdapter` | H2/JPA, schema cache with TTL, habit tracking |
| `OsKeyringCredentialStore` | `CredentialStore` | Windows CredMgr / macOS Keychain / Linux libsecret |
| `ClaudeDesktopConfigurator` | `ClientConfigurator` | Auto-injects into `claude_desktop_config.json` |
| `CursorConfigurator` | `ClientConfigurator` | Auto-injects into `.cursor/mcp.json` |
| `VsCodeConfigurator` | `ClientConfigurator` | Auto-injects into VS Code `settings.json` |

### 🟠 db-pilot-mcp (MCP Server)

3 MCP tools exposed via STDIO JSON-RPC transport:

| Tool | Name | Description |
|---|---|---|
| `QueryTool` | `translate_query` | NL → SQL/aggregation with optional execution |
| `SchemaExplorerTool` | `explore_schema` | Browse tables, columns, foreign keys |
| `HealthTool` | `health_check` | Verify all system components |

### 🟣 db-pilot-app (Application Assembly)

Spring Boot entry point + Picocli CLI + GraalVM native image profile.

| Command | Usage | Description |
|---|---|---|
| `setup` | `db-pilot setup` | Interactive wizard |
| `check` | `db-pilot check` | Health check |
| `dry-run` | `db-pilot dry-run "query" -d alias` | Generate + EXPLAIN |

---

## Query Pipeline

When a user asks "Show me all active customers ordered by revenue":

```
┌──────────────────────────────────────────────────────────────┐
│ 1. RESOLVE CONNECTION                                        │
│    CredentialStore.retrieve("prod-postgres")                  │
│    → DatabaseConnectionInfo (from OS Keyring)                │
├──────────────────────────────────────────────────────────────┤
│ 2. LOAD SCHEMA                                               │
│    KnowledgeStore.isSchemaCached("prod-postgres")?           │
│    YES → return cached (24h TTL)                             │
│    NO  → DatabaseAdapter.introspectSchema() → cache it       │
├──────────────────────────────────────────────────────────────┤
│ 3. RANK TABLES (Triple-Layer)                                │
│    Layer 1: Semantic Match (α=0.5)                           │
│      "customers" matches "customers" → score 1.0             │
│      "orders" matches "revenue" → score 0.7                  │
│    Layer 2: FK Expansion (β=0.3)                             │
│      orders → customers via FK → boost both                  │
│    Layer 3: Frequency (γ=0.2)                                │
│      User queried "customers" 15 times → boost               │
│    Result: [customers(0.87), orders(0.73), ...]              │
├──────────────────────────────────────────────────────────────┤
│ 4. COMPACT SCHEMA                                            │
│    SchemaCompactor strips audit columns (created_at, etc.)   │
│    Converts to YAML: t:customers; c:[id:int:PK, name:varchar]│
├──────────────────────────────────────────────────────────────┤
│ 5. ASSEMBLE PROMPT (PromptFactory)                           │
│    [DB-specific instructions for PostgreSQL]                  │
│    + [Ranked Schema YAML]                                    │
│    + [Global Rules: "Exclude deleted records"]               │
│    + [User Rules: "Default sort by revenue DESC"]            │
│    + [Frequently Used Tables: customers(15x)]                │
├──────────────────────────────────────────────────────────────┤
│ 6. TOKEN GUARD                                               │
│    Estimated: 2,800 tokens (limit: 4,000) → OK               │
│    If over → prune lowest-ranked tables from schema          │
├──────────────────────────────────────────────────────────────┤
│ 7. LLM GENERATION                                            │
│    Claude 4.6 Sonnet (temp=0) → deterministic SQL            │
│    "SELECT c.*, SUM(o.amount) AS revenue                     │
│     FROM customers c JOIN orders o ON c.id = o.customer_id   │
│     WHERE c.status = 'ACTIVE'                                │
│     GROUP BY c.id ORDER BY revenue DESC"                     │
├──────────────────────────────────────────────────────────────┤
│ 8. EXECUTE (if requested)                                    │
│    DatabaseAdapter.executeQuery() → QueryResult              │
│    OR DatabaseAdapter.explainQuery() → EXPLAIN plan          │
├──────────────────────────────────────────────────────────────┤
│ 9. LEARN                                                     │
│    Record table access: customers(+1), orders(+1)            │
│    If user corrects → LearningLoopService extracts rule      │
└──────────────────────────────────────────────────────────────┘
```

---

## MCP Tools API

### `translate_query`

Translates natural language into a database query.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `intent` | string | ✅ | Natural language description of the data |
| `database_alias` | string | ✅ | Target database connection alias |
| `user_id` | string | ❌ | User ID for personalization (default: "default") |
| `execute` | boolean | ❌ | Execute the query after generation (default: false) |
| `dry_run` | boolean | ❌ | Run EXPLAIN instead (default: false) |

### `explore_schema`

Browse database schema interactively.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `database_alias` | string | ✅ | Target database |
| `command` | string | ❌ | `"tables"`, `"columns:TABLE"`, or `"relations:TABLE"` |

### `health_check`

No parameters. Returns system-wide health report.

---

## CLI Commands

```bash
# Interactive setup wizard
db-pilot setup

# Health check
db-pilot check

# Dry-run mode (generate + EXPLAIN, no execution)
db-pilot dry-run "show all active users" -d prod-postgres

# Start MCP server (for AI IDE integration)
db-pilot --dbpilot.mcp.enabled=true

# Show help
db-pilot --help
```

---

## Configuration

### application.yml

```yaml
dbpilot:
  llm:
    provider: anthropic          # anthropic | openai | ollama
    api-key: ${ANTHROPIC_API_KEY}
    model: claude-sonnet-4-20250514
  token-limit: 4000             # Max tokens in prompt context
  ranking:
    semantic-weight: 0.5         # α — keyword/embedding similarity
    relation-weight: 0.3         # β — FK relation expansion
    frequency-weight: 0.2        # γ — user query frequency
```

### Environment Variables

| Variable | Description | Required |
|---|---|---|
| `ANTHROPIC_API_KEY` | Anthropic API key for Claude | Yes (for LLM) |

### Data Storage

| Data | Location | Engine |
|---|---|---|
| Knowledge Store | `~/.db-pilot/knowledge` | Embedded H2 |
| Credentials | OS Keyring | Windows CredMgr / macOS Keychain |
| Schema Cache | Knowledge Store (H2) | 24h TTL auto-refresh |

---

## Security

### Credential Storage

DB-Pilot **never** stores credentials in plain text. All database passwords are stored in the OS-native credential manager:

| OS | Backend |
|---|---|
| **Windows** | Credential Manager |
| **macOS** | Keychain |
| **Linux** | GNOME Keyring / libsecret |

### How It Works

```
db-pilot setup
  → Prompts for DB credentials
  → Tests connection
  → Stores as JSON blob in OS keyring under service "db-pilot"
  → Retrieves at runtime via java-keyring library
```

---

## Key Design Decisions

### 1. Hexagonal Purity
`db-pilot-core` has **zero** Spring/framework annotations. Domain services are plain POJOs, wired via `@Configuration` in the infrastructure module. This ensures the domain logic is testable without any framework.

### 2. Provider-Agnostic LLM
`LlmGateway` is a core port interface. The default adapter uses Anthropic Claude 4.6 Sonnet via LangChain4j, but swapping to OpenAI or Ollama requires only a configuration change.

### 3. Triple-Layer Table Ranking
Not just keyword matching — the ranker considers:
- **Semantic similarity** (50%) — do table/column names match the intent?
- **Relational proximity** (30%) — is this table FK-linked to a match?
- **User frequency** (20%) — does the user query this table often?

### 4. Token-Efficient Prompts
- `SchemaCompactor` strips audit columns (`created_at`, `updated_at`, `is_deleted`, etc.)
- `TokenGuard` enforces a 4000-token budget with progressive pruning
- Schema is compact YAML, not full DDL — saves ~60% tokens

### 5. MongoDB Native Aggregation
MongoDB queries are generated as native JSON aggregation pipelines, not shell syntax. This allows direct execution via the Java MongoDB Driver.

### 6. Atomic Learning Loop
When a user corrects a generated query:
1. Both queries (original + corrected) are sent to the LLM
2. The LLM extracts a generic business rule
3. The rule is persisted to the Knowledge Store
4. Future queries automatically include the rule in the prompt

---

## Technology Stack

| Component | Technology | Version |
|---|---|---|
| **Runtime** | Java (Virtual Threads) | 21 |
| **Framework** | Spring Boot | 3.4.5 |
| **LLM** | LangChain4j + Anthropic Claude | 1.0.0-beta3 |
| **MCP** | STDIO transport (custom JSON-RPC) | 2024-11-05 |
| **CLI** | Picocli | 4.7.6 |
| **Connection Pool** | HikariCP | via Spring Boot |
| **Knowledge Store** | Embedded H2 | 2.3.232 |
| **Credentials** | java-keyring | 1.0.4 |
| **Embeddings** | ONNX Runtime (BGE-small) | 1.20.0 |
| **Build** | Maven + GraalVM Native Profile | — |
| **DB Drivers** | PostgreSQL, Oracle 23c, MySQL 9, MariaDB 3.5, MongoDB 5.4 | — |

---

## Documentation Index

| Document | Description |
|---|---|
| [README.md](README.md) | This file — project overview |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Hexagonal architecture deep dive |
| [docs/MODULES.md](docs/MODULES.md) | Module responsibilities and class inventory |
| [docs/CONFIGURATION.md](docs/CONFIGURATION.md) | All configuration options |
| [docs/MCP_API.md](docs/MCP_API.md) | MCP tools specification |

---

## License

Proprietary — DB-Pilot © 2026