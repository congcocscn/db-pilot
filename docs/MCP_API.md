# DB-Pilot MCP API Reference

## Protocol

- **Transport:** STDIO (stdin/stdout)
- **Format:** JSON-RPC 2.0
- **Protocol Version:** `2024-11-05`

---

## Server Info

```json
{
  "name": "db-pilot",
  "version": "1.0.0"
}
```

## Capabilities

```json
{
  "tools": { "listChanged": false }
}
```

---

## Tools

### 1. `translate_query`

**Description:** Translate natural language into a database query (SQL or MongoDB aggregation pipeline) and optionally execute it.

#### Input Schema

```json
{
  "type": "object",
  "properties": {
    "intent": {
      "type": "string",
      "description": "Natural language description of the data you want"
    },
    "database_alias": {
      "type": "string",
      "description": "Target database connection alias (e.g., 'prod-postgres')"
    },
    "user_id": {
      "type": "string",
      "description": "User ID for personalization (default: 'default')"
    },
    "execute": {
      "type": "boolean",
      "description": "Whether to execute the generated query",
      "default": false
    },
    "dry_run": {
      "type": "boolean",
      "description": "Whether to run EXPLAIN instead of executing",
      "default": false
    }
  },
  "required": ["intent", "database_alias"]
}
```

#### Example Request

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "translate_query",
    "arguments": {
      "intent": "Show me all active customers ordered by total revenue",
      "database_alias": "prod-postgres",
      "user_id": "john",
      "execute": false,
      "dry_run": true
    }
  }
}
```

#### Example Response

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [{
      "type": "text",
      "text": "## Generated Query\n```sql\nSELECT c.*, SUM(o.amount) AS revenue\nFROM customers c\nJOIN orders o ON c.id = o.customer_id\nWHERE c.status = 'ACTIVE'\nGROUP BY c.id\nORDER BY revenue DESC\n```\n\n📊 Used 3 tables in context (1,200 tokens)\nTop tables: customers (0.87) orders (0.73) payments (0.45)\n\n### Explain Plan\n```\nSort  (cost=124.50..124.75 rows=100 width=200)\n  Sort Key: revenue DESC\n  ->  HashAggregate  ...\n```"
    }]
  }
}
```

#### Pipeline Stages

When this tool is called, the following pipeline executes:

1. Retrieve credentials from OS Keyring
2. Load schema (from cache or live introspection)
3. Rank tables using triple-layer algorithm
4. Compact schema to YAML (strip audit columns)
5. Assemble prompt (schema + rules + habits + intent)
6. Enforce token budget (prune if >4000 tokens)
7. Send to Claude 4.6 Sonnet (temperature=0)
8. Optionally execute or EXPLAIN the query
9. Record table access for future ranking

---

### 2. `explore_schema`

**Description:** Explore database schema — list tables, describe columns, find foreign key relationships.

#### Input Schema

```json
{
  "type": "object",
  "properties": {
    "database_alias": {
      "type": "string",
      "description": "Target database connection alias"
    },
    "command": {
      "type": "string",
      "description": "What to explore: 'tables', 'columns:TABLE_NAME', 'relations:TABLE_NAME'",
      "default": "tables"
    }
  },
  "required": ["database_alias"]
}
```

#### Commands

| Command | Description | Example |
|---|---|---|
| `tables` | List all tables with column count and row estimates | `"tables"` |
| `columns:TABLE` | Show column details for a specific table | `"columns:customers"` |
| `relations:TABLE` | Show foreign key relationships | `"relations:orders"` |

#### Example: List Tables

```json
{
  "params": {
    "name": "explore_schema",
    "arguments": {
      "database_alias": "prod-postgres",
      "command": "tables"
    }
  }
}
```

Response:
```
## Tables in 'prod-postgres' (15 total)

| Table | Columns | Est. Rows |
| --- | --- | --- |
| customers | 12 | 50000 |
| orders | 8 | 250000 |
| products | 6 | 1200 |
| ... | ... | ... |
```

#### Example: Describe Columns

```json
{
  "params": {
    "name": "explore_schema",
    "arguments": {
      "database_alias": "prod-postgres",
      "command": "columns:customers"
    }
  }
}
```

Response:
```
## Columns of 'customers'

| Column | Type | PK | Nullable |
| --- | --- | --- | --- |
| id | integer | ✅ | NO |
| name | character varying(100) | | YES |
| email | character varying(255) | | NO |
| status | character varying(20) | | NO |
| ... | ... | ... | ... |
```

---

### 3. `health_check`

**Description:** Check DB-Pilot system health: database connectivity, knowledge base status, LLM availability.

#### Input Schema

```json
{
  "type": "object",
  "properties": {}
}
```

#### Example

```json
{
  "params": {
    "name": "health_check",
    "arguments": {}
  }
}
```

Response:
```
✅ DB-Pilot Health: All systems operational

  ✅ PostgreSQL: prod-postgres — Connected (45ms)
  ✅ Oracle: analytics-oracle — Connected (120ms)
  ✅ Knowledge Store — 12 rules, 45 habits, 3 cached schemas
  ✅ LLM Provider — Anthropic Claude 4.6 Sonnet available (830ms)
  ✅ Credential Store — OS Keyring accessible
```

---

## JSON-RPC Protocol

### Initialize

```json
// Request
{"jsonrpc":"2.0","id":0,"method":"initialize","params":{}}

// Response
{
  "jsonrpc": "2.0",
  "id": 0,
  "result": {
    "protocolVersion": "2024-11-05",
    "capabilities": {"tools": {"listChanged": false}},
    "serverInfo": {"name": "db-pilot", "version": "1.0.0"}
  }
}
```

### List Tools

```json
// Request
{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}

// Response
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "tools": [
      {"name": "translate_query", "description": "...", "inputSchema": {...}},
      {"name": "explore_schema", "description": "...", "inputSchema": {...}},
      {"name": "health_check", "description": "...", "inputSchema": {...}}
    ]
  }
}
```

### Call Tool

```json
// Request
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "translate_query",
    "arguments": {"intent": "...", "database_alias": "..."}
  }
}

// Response
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "content": [{"type": "text", "text": "..."}]
  }
}
```

### Error Response

```json
{
  "jsonrpc": "2.0",
  "id": "error",
  "error": {
    "code": -32603,
    "message": "Internal error: Connection refused"
  }
}
```
