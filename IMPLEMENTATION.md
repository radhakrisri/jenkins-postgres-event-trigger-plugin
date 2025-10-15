# Implementation Details

## Overview

This Jenkins plugin enables continuous monitoring of Postgres database tables through Supabase's Realtime feature and triggers Jenkins builds when database events (INSERT, UPDATE, DELETE) occur.

## Architecture

### Core Components

#### 1. SupabaseInstance
- **Purpose**: Represents a Supabase instance configuration
- **Storage**: Stores instance name, URL, and API key credential reference
- **Key Features**:
  - Integrates with Jenkins credentials system for secure API key storage
  - Validates connection parameters
  - Supports multiple instances per Jenkins server

#### 2. PostgresEventTriggerConfiguration
- **Purpose**: Global Jenkins configuration for managing Supabase instances
- **Storage**: Persists configuration using Jenkins configuration system
- **Key Features**:
  - Allows administrators to configure multiple Supabase instances
  - Provides lookup functionality for instances by name
  - Validates instance configurations

#### 3. SupabaseRealtimeClient
- **Purpose**: WebSocket client for Supabase Realtime protocol
- **Technology**: Uses Java-WebSocket library
- **Key Features**:
  - Establishes WebSocket connection to Supabase
  - Implements Phoenix protocol for Realtime
  - Manages subscriptions to table events
  - Sends periodic heartbeats to maintain connection
  - Handles event callbacks

#### 4. PostgresEventTrigger
- **Purpose**: Jenkins trigger that monitors database events
- **Type**: Extends `hudson.triggers.Trigger`
- **Key Features**:
  - Configurable per-job
  - Subscribes to multiple tables simultaneously
  - Filters events by type (INSERT, UPDATE, DELETE)
  - Creates build causes with event metadata
  - Passes event data as build parameters

### Data Flow

```
Database Event → Supabase Realtime → WebSocket → SupabaseRealtimeClient 
→ PostgresEventTrigger → Jenkins Build Scheduler → Job Execution
```

1. **Event Detection**: Database change occurs in Postgres
2. **Realtime Broadcast**: Supabase broadcasts event via WebSocket
3. **Event Reception**: SupabaseRealtimeClient receives the event
4. **Handler Invocation**: Registered event handler is called
5. **Build Triggering**: PostgresEventTrigger schedules a new build
6. **Parameter Injection**: Event data is passed as build parameters

## Configuration

### Global Configuration (System Level)

Location: Manage Jenkins → Configure System

Administrators configure:
- Supabase instance name (unique identifier)
- Supabase URL (project URL)
- API key credentials (from Jenkins credentials store)

### Job Configuration (Job Level)

Location: Job Configuration → Build Triggers

Users configure:
- Which Supabase instance to monitor
- List of tables to monitor
- Which events to subscribe to (INSERT, UPDATE, DELETE)

## WebSocket Protocol

### Connection

The plugin connects to Supabase Realtime using WebSocket:
- URL format: `wss://[project-id].supabase.co/realtime/v1/websocket`
- Authentication: API key sent as header
- Protocol: Phoenix Channels

### Message Format

All messages follow Phoenix protocol format:
```json
{
  "topic": "realtime:schema:table",
  "event": "event_type",
  "ref": "unique_reference",
  "payload": {}
}
```

### Subscription

To subscribe to a table:
```json
{
  "topic": "realtime:public:users",
  "event": "phx_join",
  "ref": "1",
  "payload": {"config": "{}"}
}
```

### Heartbeat

Periodic heartbeat to maintain connection:
```json
{
  "topic": "phoenix",
  "event": "heartbeat",
  "ref": "2",
  "payload": {}
}
```

## Build Parameters

When a build is triggered, the following parameters are available:

1. **POSTGRES_EVENT_TYPE**: INSERT, UPDATE, or DELETE
2. **POSTGRES_TABLE_NAME**: Name of the table that triggered the event
3. **POSTGRES_EVENT_DATA**: JSON string with full event payload

### Event Payload Structure

The payload contains the record data and metadata from Supabase:
```json
{
  "type": "INSERT",
  "schema": "public",
  "table": "users",
  "record": {
    "id": 1,
    "name": "John Doe",
    "created_at": "2025-01-01T12:00:00Z"
  },
  "old_record": null
}
```

## Security

### Credential Management

- API keys stored using Jenkins Credentials Plugin
- Uses StringCredentials type for secret text
- Keys never exposed in logs or UI
- Access controlled by Jenkins permissions

### Connection Security

- WebSocket connections use TLS (wss://)
- API key authentication for all requests
- Supabase RLS (Row Level Security) policies apply

## Error Handling

### Connection Failures

- Logs connection errors at SEVERE level
- Attempts to reconnect on connection drop
- Job configuration remains valid, reconnects on next trigger start

### Invalid Configuration

- Validates instance name exists before connecting
- Checks table names are not empty
- Ensures at least one event type is selected
- Provides user feedback via FormValidation

### Event Processing Errors

- Catches and logs exceptions during event handling
- Continues processing subsequent events
- Does not crash the Jenkins job

## Testing

### Unit Tests

Three test classes provide coverage:

1. **SupabaseInstanceTest**: Tests instance creation and properties
2. **PostgresEventTriggerConfigurationTest**: Tests global configuration management
3. **PostgresEventTriggerTest**: Tests trigger creation and configuration

### Test Infrastructure

- Uses JenkinsRule for integration testing
- Tests run in isolated Jenkins instances
- Mocks WebSocket connections for reliability

## Performance Considerations

### Memory Usage

- One WebSocket connection per active trigger
- Event handlers use weak references where possible
- Subscriptions cleaned up on trigger stop

### Network Traffic

- Minimal overhead: heartbeats every 30 seconds
- Only subscribed events are received
- JSON parsing is efficient using Gson

### Build Scheduling

- Builds scheduled asynchronously
- No blocking on WebSocket thread
- Queue management handled by Jenkins core

## Limitations

### Current Limitations

1. No automatic reconnection on network failure (Jenkins restart required)
2. No support for filtering events by record values
3. No built-in retry mechanism for failed builds
4. Requires Supabase Realtime to be enabled on tables

### Future Enhancements

Potential improvements:
- Auto-reconnect on connection loss
- Event filtering by column values
- Batch event processing
- Support for custom event handlers
- Integration with Jenkins pipeline DSL
- Support for other Postgres-compatible databases with realtime features

## Dependencies

### Runtime Dependencies

- **Jenkins Core**: 2.361.4+
- **Credentials Plugin**: For API key management
- **Plain Credentials Plugin**: For secret text credentials
- **Java-WebSocket**: 1.5.6 for WebSocket support
- **Gson**: 2.10.1 for JSON processing

### Development Dependencies

- **JenkinsRule**: For testing
- **JUnit**: 4.x for unit tests
- **Structs Plugin**: For test infrastructure

## Best Practices

### For Users

1. Use separate Supabase instances for production and testing
2. Create dedicated service role keys for Jenkins
3. Enable only necessary events to reduce noise
4. Use Jenkins job parameters to handle event data
5. Implement idempotent build logic (events may duplicate)

### For Developers

1. Always clean up WebSocket connections in stop()
2. Use logging appropriately (INFO for events, FINE for details)
3. Handle JSON parsing errors gracefully
4. Test with real Supabase instances before release
5. Document configuration changes in help files

## Troubleshooting Guide

### Common Issues

#### "No instances configured"
- **Cause**: No Supabase instances added in global configuration
- **Solution**: Add instance in Manage Jenkins → Configure System

#### "Failed to connect to Supabase Realtime"
- **Cause**: Invalid URL or API key, network issues
- **Solution**: Verify URL format and credentials, check network connectivity

#### "Events not triggering builds"
- **Cause**: Realtime not enabled, wrong table name, no events selected
- **Solution**: Enable Realtime in Supabase, verify table names, check event checkboxes

#### WebSocket Connection Drops
- **Cause**: Network instability, Supabase maintenance
- **Solution**: Restart Jenkins or the specific job

### Debug Logging

Enable debug logging for troubleshooting:
1. Manage Jenkins → System Log
2. Add new log recorder
3. Add logger: `io.jenkins.plugins.postgres`
4. Set level: ALL or FINE

## Compliance

### License

MIT License - See LICENSE file

### Privacy

- No data sent to third parties
- Event data stored only in Jenkins builds
- Credentials encrypted by Jenkins

### Compatibility

- Jenkins LTS versions 2.361.4+
- Java 11 or later
- All major operating systems (Linux, Windows, macOS)
