# Architecture Diagram

## System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Supabase Cloud                              │
│                                                                       │
│  ┌──────────────┐         ┌─────────────────┐                       │
│  │   Postgres   │────────▶│  Realtime API   │                       │
│  │   Database   │         │  (WebSocket)    │                       │
│  └──────────────┘         └─────────────────┘                       │
│        │                           │                                 │
│        │ INSERT/UPDATE/DELETE      │ Broadcasts                     │
│        ▼                           │ Events                          │
│  ┌──────────────┐                  │                                │
│  │    Tables    │                  │                                │
│  │ - users      │                  │                                │
│  │ - orders     │                  │                                │
│  │ - products   │                  │                                │
│  └──────────────┘                  │                                │
└────────────────────────────────────┼────────────────────────────────┘
                                     │
                                     │ WebSocket
                                     │ Connection (WSS)
                                     │
┌────────────────────────────────────┼────────────────────────────────┐
│                     Jenkins Server │                                 │
│                                    ▼                                 │
│  ┌─────────────────────────────────────────────────┐                │
│  │         SupabaseRealtimeClient                  │                │
│  │  - Establishes WebSocket connection             │                │
│  │  - Manages subscriptions to tables              │                │
│  │  - Receives events from Supabase                │                │
│  │  - Sends heartbeat to keep connection alive     │                │
│  └────────────────┬────────────────────────────────┘                │
│                   │                                                  │
│                   │ Event Notifications                              │
│                   ▼                                                  │
│  ┌─────────────────────────────────────────────────┐                │
│  │         PostgresEventTrigger                    │                │
│  │  - Configured per Jenkins job                   │                │
│  │  - Listens for specific table events            │                │
│  │  - Filters by event type (INSERT/UPDATE/DELETE) │                │
│  └────────────────┬────────────────────────────────┘                │
│                   │                                                  │
│                   │ Triggers Build                                   │
│                   ▼                                                  │
│  ┌─────────────────────────────────────────────────┐                │
│  │         Jenkins Build Queue                     │                │
│  │  - Schedules new build with parameters          │                │
│  │  - POSTGRES_EVENT_TYPE                          │                │
│  │  - POSTGRES_TABLE_NAME                          │                │
│  │  - POSTGRES_EVENT_DATA (JSON)                   │                │
│  └────────────────┬────────────────────────────────┘                │
│                   │                                                  │
│                   │ Executes                                         │
│                   ▼                                                  │
│  ┌─────────────────────────────────────────────────┐                │
│  │         Jenkins Job (Build)                     │                │
│  │  - Accesses event data via env variables        │                │
│  │  - Executes custom build logic                  │                │
│  │  - Can deploy, notify, transform, etc.          │                │
│  └─────────────────────────────────────────────────┘                │
│                                                                       │
└───────────────────────────────────────────────────────────────────────┘
```

## Data Flow Sequence

```
1. Database Event
   User/App → Postgres Database
   └─▶ INSERT INTO users (name) VALUES ('John')

2. Realtime Broadcast
   Postgres → Supabase Realtime
   └─▶ Broadcasts event via WebSocket

3. Event Reception
   Supabase Realtime → SupabaseRealtimeClient
   └─▶ Receives JSON event payload

4. Event Processing
   SupabaseRealtimeClient → PostgresEventTrigger
   └─▶ Invokes registered event handler

5. Build Scheduling
   PostgresEventTrigger → Jenkins Queue
   └─▶ Schedules build with parameters

6. Build Execution
   Jenkins Queue → Jenkins Job
   └─▶ Executes build steps with event data

7. Custom Logic
   Jenkins Job → External Systems
   └─▶ Deploy, notify, transform, etc.
```

## Component Interaction

```
┌───────────────────────────────────────────────────────────────┐
│                    Jenkins Global Config                       │
│                                                                 │
│  ┌────────────────────────────────────────────────┐            │
│  │  PostgresEventTriggerConfiguration             │            │
│  │                                                 │            │
│  │  Manages:                                       │            │
│  │  - List of SupabaseInstance objects            │            │
│  │  - Instance name → URL + Credentials mapping   │            │
│  └────────────────────────────────────────────────┘            │
│                          │                                      │
│                          │ Provides instances to               │
│                          ▼                                      │
│  ┌────────────────────────────────────────────────┐            │
│  │  SupabaseInstance                              │            │
│  │                                                 │            │
│  │  Contains:                                      │            │
│  │  - name: "production-db"                       │            │
│  │  - url: "https://xxx.supabase.co"             │            │
│  │  - credentialsId: "api-key-credential"        │            │
│  └────────────────────────────────────────────────┘            │
│                                                                 │
└───────────────────────────────────────────────────────────────┘
                                  │
                                  │ Referenced by
                                  ▼
┌───────────────────────────────────────────────────────────────┐
│                      Jenkins Job Config                        │
│                                                                 │
│  ┌────────────────────────────────────────────────┐            │
│  │  PostgresEventTrigger                          │            │
│  │                                                 │            │
│  │  Configuration:                                 │            │
│  │  - instanceName: "production-db"               │            │
│  │  - tables: "users, orders"                     │            │
│  │  - subscribeInsert: true                       │            │
│  │  - subscribeUpdate: true                       │            │
│  │  - subscribeDelete: false                      │            │
│  └────────────────────────────────────────────────┘            │
│                          │                                      │
│                          │ Creates and manages                 │
│                          ▼                                      │
│  ┌────────────────────────────────────────────────┐            │
│  │  SupabaseRealtimeClient                        │            │
│  │                                                 │            │
│  │  Responsibilities:                              │            │
│  │  - WebSocket connection management             │            │
│  │  - Table subscription management               │            │
│  │  - Event handler registration                  │            │
│  │  - Heartbeat management                        │            │
│  └────────────────────────────────────────────────┘            │
│                                                                 │
└───────────────────────────────────────────────────────────────┘
```

## Event Message Format

### Subscription Message (Jenkins → Supabase)
```json
{
  "topic": "realtime:public:users",
  "event": "phx_join",
  "ref": "1",
  "payload": {
    "config": "{}"
  }
}
```

### Event Message (Supabase → Jenkins)
```json
{
  "topic": "realtime:public:users",
  "event": "INSERT",
  "ref": null,
  "payload": {
    "type": "INSERT",
    "schema": "public",
    "table": "users",
    "record": {
      "id": 123,
      "name": "John Doe",
      "email": "john@example.com",
      "created_at": "2025-01-01T12:00:00Z"
    },
    "old_record": null
  }
}
```

### Heartbeat Message (Jenkins → Supabase)
```json
{
  "topic": "phoenix",
  "event": "heartbeat",
  "ref": "2",
  "payload": {}
}
```

## State Diagram

```
                  ┌─────────────┐
                  │   STOPPED   │
                  └──────┬──────┘
                         │
                         │ Job Configured & Started
                         ▼
                  ┌─────────────┐
                  │ CONNECTING  │
                  └──────┬──────┘
                         │
                         │ WebSocket Connection Established
                         ▼
                  ┌─────────────┐
                  │ SUBSCRIBING │
                  └──────┬──────┘
                         │
                         │ Table Subscriptions Sent
                         ▼
                  ┌─────────────┐
                  │   ACTIVE    │◀────┐
                  └──────┬──────┘     │
                         │            │
                         │ Event      │ Heartbeat
                         │ Received   │ (every 30s)
                         ▼            │
                  ┌─────────────┐     │
                  │ TRIGGERING  │─────┘
                  └──────┬──────┘
                         │
                         │ Build Scheduled
                         ▼
                  ┌─────────────┐
                  │   ACTIVE    │
                  └──────┬──────┘
                         │
                         │ Job Stopped/Deleted
                         ▼
                  ┌─────────────┐
                  │ CLEANING UP │
                  └──────┬──────┘
                         │
                         │ Unsubscribed & Disconnected
                         ▼
                  ┌─────────────┐
                  │   STOPPED   │
                  └─────────────┘
```

## Deployment Scenarios

### Scenario 1: Single Instance, Single Job
```
Supabase Instance: "production"
    │
    └─▶ Jenkins Job: "Deploy on User Changes"
        └─▶ Tables: users
        └─▶ Events: INSERT, UPDATE
```

### Scenario 2: Single Instance, Multiple Jobs
```
Supabase Instance: "production"
    │
    ├─▶ Jenkins Job: "User Analytics"
    │   └─▶ Tables: users
    │   └─▶ Events: INSERT, UPDATE, DELETE
    │
    ├─▶ Jenkins Job: "Order Processing"
    │   └─▶ Tables: orders
    │   └─▶ Events: INSERT
    │
    └─▶ Jenkins Job: "Data Sync"
        └─▶ Tables: users, orders, products
        └─▶ Events: INSERT, UPDATE
```

### Scenario 3: Multiple Instances, Multiple Jobs
```
Supabase Instance: "production"
    │
    ├─▶ Jenkins Job: "Prod User Analytics"
    │   └─▶ Tables: users
    │
    └─▶ Jenkins Job: "Prod Order Processing"
        └─▶ Tables: orders

Supabase Instance: "staging"
    │
    ├─▶ Jenkins Job: "Staging Tests"
    │   └─▶ Tables: users, orders
    │
    └─▶ Jenkins Job: "Integration Tests"
        └─▶ Tables: test_data
```

## Performance Characteristics

### Resource Usage per Active Trigger

```
┌─────────────────────┬──────────────────────────────┐
│ Resource            │ Usage                        │
├─────────────────────┼──────────────────────────────┤
│ Memory              │ ~5-10 MB per connection      │
│ Network (idle)      │ ~1 KB/30s (heartbeat)        │
│ Network (events)    │ Variable (event size)        │
│ CPU (idle)          │ <1%                          │
│ CPU (event)         │ <5% (burst)                  │
│ Threads             │ 2-3 per connection           │
└─────────────────────┴──────────────────────────────┘
```

### Scalability Limits

```
┌─────────────────────────┬────────────────────────┐
│ Metric                  │ Recommended Limit      │
├─────────────────────────┼────────────────────────┤
│ Active Triggers         │ <50 per Jenkins        │
│ Tables per Trigger      │ <10                    │
│ Events per Second       │ <100                   │
│ Concurrent Builds       │ Jenkins default limits │
└─────────────────────────┴────────────────────────┘
```

Note: These are conservative estimates. Actual limits depend on:
- Jenkins server resources
- Network bandwidth
- Event payload size
- Build execution time
