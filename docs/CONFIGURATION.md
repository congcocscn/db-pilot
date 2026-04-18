# DB-Pilot Configuration Guide

## Application Configuration (`application.yml`)

The main configuration file is located at:
```
db-pilot-app/src/main/resources/application.yml
```

### Full Configuration Reference

```yaml
spring:
  application:
    name: db-pilot

  # Virtual Threads (Java 21+) — enables lightweight threading for JDBC I/O
  threads:
    virtual:
      enabled: true                  # Default: true

  # Embedded H2 Knowledge Store
  datasource:
    url: jdbc:h2:file:${user.home}/.db-pilot/knowledge;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password:                        # No password for local embedded DB

  jpa:
    hibernate:
      ddl-auto: update               # Auto-creates/updates schema on startup
    show-sql: false                   # Set true for debugging JPA queries

  main:
    banner-mode: log                  # Redirect banner to log (stdout = MCP)

# ==========================================
# DB-Pilot Custom Settings
# ==========================================
dbpilot:
  # LLM Configuration
  llm:
    provider: anthropic              # Options: anthropic, openai, ollama
    api-key: ${ANTHROPIC_API_KEY:}   # From environment variable
    model: claude-sonnet-4-20250514     # Claude 4.6 Sonnet

  # Token Guard — maximum tokens in assembled prompt
  token-limit: 4000                   # Default: 4000

  # Table Ranking Weights (must sum to 1.0)
  ranking:
    semantic-weight: 0.5              # α — keyword/embedding similarity
    relation-weight: 0.3              # β — FK relation expansion boost
    frequency-weight: 0.2             # γ — user query frequency boost

  # MCP Server (activated via --dbpilot.mcp.enabled=true)
  mcp:
    enabled: false                    # Set true to start STDIO server

# ==========================================
# Logging
# ==========================================
logging:
  pattern:
    console: "%d{HH:mm:ss.SSS} [%X{traceId:-none}][%X{userId:-system}][%X{dbId:-}][%X{action:-}] %-5level %logger{36} — %msg%n"
  level:
    com.dbpilot: INFO                 # Application logging
    dev.langchain4j: WARN             # LangChain4j (reduce noise)
    com.zaxxer.hikari: WARN           # HikariCP pool logging
    org.hibernate: WARN               # Hibernate SQL logging
```

---

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `ANTHROPIC_API_KEY` | Yes (for LLM) | — | Anthropic API key for Claude |
| `OPENAI_API_KEY` | No | — | OpenAI API key (if using OpenAI provider) |

### Setting Environment Variables

**Windows (PowerShell):**
```powershell
$env:ANTHROPIC_API_KEY = "sk-ant-api03-..."
```

**Windows (permanent):**
```cmd
setx ANTHROPIC_API_KEY "sk-ant-api03-..."
```

**Linux/macOS:**
```bash
export ANTHROPIC_API_KEY="sk-ant-api03-..."
```

---

## Database Connection Setup

Database connections are configured interactively via `db-pilot setup` and stored in the OS Keyring.

### Connection Parameters

| Parameter | Description | Example |
|---|---|---|
| Alias | Unique name for this connection | `prod-postgres` |
| Type | Database engine | PostgreSQL, Oracle, MySQL, MariaDB, MongoDB |
| Host | Server hostname or IP | `localhost`, `db.example.com` |
| Port | Server port | 5432 (PG), 1521 (Oracle), 3306 (MySQL), 27017 (Mongo) |
| Database Name | Database/schema name | `myapp_production` |
| Schema | Schema to introspect (optional) | `public` (PG), `HR` (Oracle) |
| Username | DB user | `readonly_user` |
| Password | DB password (stored in keyring) | — |

### Default Ports

| Engine | Default Port |
|---|---|
| PostgreSQL | 5432 |
| Oracle | 1521 |
| MySQL | 3306 |
| MariaDB | 3306 |
| MongoDB | 27017 |

---

## AI Client Configuration

### Auto-Configuration (via Setup Wizard)

The setup wizard auto-detects and configures these AI clients:

#### Claude Desktop
- **Config file:** `%APPDATA%\Claude\claude_desktop_config.json` (Windows)
- **Injected entry:**
```json
{
  "mcpServers": {
    "db-pilot": {
      "command": "java -jar /path/to/db-pilot.jar",
      "args": ["--mcp"]
    }
  }
}
```

#### Cursor IDE
- **Config file:** `~/.cursor/mcp.json`
- **Injected entry:**
```json
{
  "mcpServers": {
    "db-pilot": {
      "command": "java -jar /path/to/db-pilot.jar",
      "args": ["--mcp"]
    }
  }
}
```

#### VS Code
- **Config file:** `%APPDATA%\Code\User\settings.json` (Windows)
- **Injected entry:**
```json
{
  "mcp": {
    "servers": {
      "db-pilot": {
        "type": "stdio",
        "command": "java -jar /path/to/db-pilot.jar",
        "args": ["--mcp"]
      }
    }
  }
}
```

### Manual Configuration

If the auto-configurator fails, add the entries above manually to the respective config files.

---

## Knowledge Store

### Location
```
~/.db-pilot/knowledge.mv.db       (H2 database file)
~/.db-pilot/knowledge.trace.db    (H2 trace file)
```

### Tables

| Table | Purpose | Key Columns |
|---|---|---|
| `business_rules` | Learned business rules | ruleText, scope (GLOBAL/USER), confidence |
| `user_habits` | Table query frequency | userId, tableName, queryCount |
| `schema_metadata` | Cached database schemas | databaseAlias, schemaJson (CLOB), cachedAt |

### Cache TTL

Schema cache expires after **24 hours** (1440 minutes). On next query after expiry, DB-Pilot automatically re-introspects the database.

---

## GraalVM Native Image

### Build Native Binary

```bash
# Requires GraalVM JDK 21+
mvn package -Pnative -DskipTests
```

### Output
```
db-pilot-app/target/db-pilot    (Linux/macOS binary)
db-pilot-app/target/db-pilot.exe (Windows binary)
```

### Profile Configuration

The native image profile is in `db-pilot-app/pom.xml`:
```xml
<profile>
    <id>native</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.graalvm.buildtools</groupId>
                <artifactId>native-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.dbpilot.app.DbPilotApplication</mainClass>
                    <imageName>db-pilot</imageName>
                    <buildArgs>
                        <buildArg>--no-fallback</buildArg>
                    </buildArgs>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

---

## Tuning Recommendations

### Token Limit

- **Small schemas** (<20 tables): `token-limit: 2000`
- **Medium schemas** (20-100 tables): `token-limit: 4000` (default)
- **Large schemas** (100+ tables): `token-limit: 6000`

### Ranking Weights

- **New user** (no history): Set `frequency-weight: 0.0`, increase `semantic-weight: 0.7`
- **Power user** (lots of history): Default weights work well
- **Complex schemas** (many FKs): Increase `relation-weight: 0.4`

### HikariCP Pool

Connection pools are auto-created per database alias with:
- Max pool size: 5
- Min idle: 1
- Connection timeout: 10s
- Idle timeout: 5min
- Max lifetime: 10min
