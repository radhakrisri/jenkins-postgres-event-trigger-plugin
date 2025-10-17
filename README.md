# Jenkins Supabase Plugin

A comprehensive Jenkins plugin for Supabase integration that enables real-time event triggers, post-build actions, and database operations.

## Features

### Current Features
- **Real-time Event Monitoring**: Subscribe to INSERT, UPDATE, and DELETE events on Postgres tables
- **Multiple Supabase Instances**: Configure and manage multiple Supabase instances from Jenkins global configuration
- **Flexible Table Selection**: Monitor one or more tables per job, with support for schema specification
- **Event Data Access**: Event data is passed to builds as environment variables
- **Secure Credential Management**: Uses Jenkins credentials for API key storage
- **Build Data Recording**: Comprehensive post-build action to record detailed build information to Supabase

### Planned Features
- **Database Query Actions**: Run custom SQL queries as build steps
- **Row-Level Security Integration**: Manage RLS policies through Jenkins
- **Storage Operations**: Upload/download files to/from Supabase Storage
- **Edge Functions Integration**: Deploy and manage Supabase Edge Functions

## Requirements

- Jenkins 2.516.3 or later
- Java 21 or later
- A Supabase project with Realtime enabled

## Installation

### From Release

1. Download the latest `jenkins-supabase.hpi` file from the [releases page](https://github.com/radhakrisri/jenkins-supabase/releases)
2. Install through Jenkins Plugin Manager: **Manage Jenkins** → **Manage Plugins** → **Advanced** → **Upload Plugin**

### Building from Source

```bash
git clone https://github.com/radhakrisri/jenkins-supabase.git
cd jenkins-supabase
mvn clean package
```

This will generate `target/jenkins-supabase.hpi` which can be installed in Jenkins.

## Configuration

### Global Configuration

1. Navigate to **Manage Jenkins** → **Configure System**
2. Find the **Postgres/Supabase Event Trigger Configuration** section
3. Click **Add** to configure a Supabase instance:
   - **Instance Name**: A unique identifier for this Supabase instance
   - **Supabase URL**: Your Supabase project URL (e.g., `https://xxxxx.supabase.co`)
   - **API Key Credentials**: Select a credential of type "Secret text" containing your Supabase API key

#### Creating API Key Credentials

1. Navigate to **Manage Jenkins** → **Manage Credentials**
2. Select the appropriate domain (usually "Global")
3. Click **Add Credentials**
4. Choose **Secret text** as the kind
5. Paste your Supabase anon or service role API key in the **Secret** field
6. Provide an ID and description
7. Click **OK**

### Job Configuration

#### Event Triggers

1. Create or configure a Jenkins job (Freestyle or Pipeline)
2. In the job configuration, under **Build Triggers**, check **Supabase Event Trigger**
3. Configure the trigger:
   - **Supabase Instance**: Select the instance to monitor
   - **Tables**: Enter comma-separated table names (e.g., `users, orders` or `public.users, myschema.orders`)
   - **Subscribe to Events**: Check the events you want to monitor (INSERT, UPDATE, DELETE)

#### Build Data Recording (Post-Build Action)

**Prerequisites:** Before using the Build Recorder, you must create the required database tables:

1. **Create tables using SQL script**:
   - Use the provided `create_build_recorder_tables.sql` file
   - Execute it in your Supabase SQL Editor or via migration
   - This creates the `jobs` metadata table and job-specific build tables

2. **Or manually create tables** using the schemas documented below

**Job Configuration:**

1. In the job configuration, under **Post-build Actions**, add **Record Build Data to Supabase**
2. Configure the recorder:
   - **Supabase Instance**: Select the instance to record data to
   - **Record Artifacts**: Include build artifact information
   - **Record Stages**: Include pipeline stage information (for pipeline builds)
   - **Record Test Results**: Include test result summaries
   - **Record Environment Variables**: Include environment variables (sensitive ones filtered)
   - **Custom Fields**: Add custom JSON data to each build record

The build recorder:
- Stores build data in tables named `builds_{job_path}`
- Maintains job metadata in a central `jobs` table
- Uses Supabase REST API for data insertion
- Records comprehensive build information including timing, results, artifacts, and more

## Usage

### Event Triggers

#### Accessing Event Data in Builds

When a build is triggered by a database event, the following environment variables are available:

- `POSTGRES_EVENT_TYPE`: The type of event (INSERT, UPDATE, or DELETE)
- `POSTGRES_TABLE_NAME`: The name of the table that triggered the event
- `POSTGRES_EVENT_DATA`: JSON string containing the full event payload from Supabase

### Build Data Recording

#### Recorded Data

The build recorder captures comprehensive information about each build:

**Basic Build Information:**
- Build number, ID, URL, result, and duration
- Start/end times and queue time
- Node and executor information
- Workspace path

**Build Context:**
- Build causes and triggers
- SCM information (if available)
- Environment variables (optional, sensitive ones filtered)

**Build Artifacts:**
- Artifact filenames, paths, and download URLs
- Artifact metadata

**Test Results:**
- Test counts (total, passed, failed, skipped)
- Links to detailed test reports
- Test result summaries

**Pipeline Stages:**
- Stage information for pipeline builds
- Stage execution data (when available)

**Custom Data:**
- User-defined JSON fields
- Additional metadata specific to your use case

#### Database Schema

**Important:** The Build Recorder requires manual table creation. Tables are NOT created automatically to prevent race conditions and ensure proper schema control.

**Table Creation Methods:**

1. **Using the provided SQL script** (recommended):
   ```bash
   # Use the create_build_recorder_tables.sql file
   # Execute in Supabase SQL Editor or via migration
   ```

2. **Manually create tables** with the following schemas:

**Jobs Metadata Table (`jobs`):**
```sql
CREATE TABLE IF NOT EXISTS jobs (
    id BIGSERIAL PRIMARY KEY,
    job_name TEXT NOT NULL,
    job_full_name TEXT UNIQUE NOT NULL,
    job_display_name TEXT,
    table_name TEXT NOT NULL,
    job_type TEXT,
    job_url TEXT,
    folder_path TEXT,
    configuration JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

COMMENT ON TABLE jobs IS 'Metadata about Jenkins jobs using Supabase Build Recorder';
```

**Job-Specific Build Tables (`builds_{job_path}`):**

For each job, create a table named after the job path (e.g., `builds_my_project`):

```sql
CREATE TABLE IF NOT EXISTS builds_{job_path} (
    id BIGSERIAL PRIMARY KEY,
    build_number INTEGER NOT NULL,
    build_id TEXT,
    build_url TEXT,
    result TEXT,
    duration_ms BIGINT,
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    queue_time_ms BIGINT,
    node_name TEXT,
    executor_info JSONB,
    workspace_path TEXT,
    causes JSONB,
    artifacts JSONB,
    test_results JSONB,
    stages JSONB,
    environment_variables JSONB,
    custom_data JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(build_number)
);

COMMENT ON TABLE builds_{job_path} IS 'Build data for job {job_name}';
```

**Table Naming Convention:**
- Job path `my-project` → table `builds_my_project`
- Job path `folder/sub-job` → table `builds_folder_sub_job`
- Special characters replaced with underscores
- Prefixed with `builds_` for consistency

The plugin automatically creates two types of tables:

1. **Jobs Metadata Table** (`jobs`):
   - Tracks all jobs using the plugin
   - Stores job configuration and metadata
   - Maps jobs to their corresponding build tables

2. **Job-Specific Build Tables** (`builds_{job_path}`):
   - One table per job based on the job path
   - Stores all build records for that job
   - Indexed for efficient querying by build number, result, and time

#### Example Queries

```sql
-- Get all jobs using the plugin
SELECT job_name, job_display_name, table_name, created_at 
FROM jobs 
WHERE is_active = true;

-- Get recent build results for a specific job
SELECT build_number, result, duration_ms, start_time, end_time 
FROM builds_my_project 
ORDER BY build_number DESC 
LIMIT 10;

-- Get build success rate over time
SELECT 
    DATE(start_time) as build_date,
    COUNT(*) as total_builds,
    COUNT(*) FILTER (WHERE result = 'SUCCESS') as successful_builds,
    ROUND(COUNT(*) FILTER (WHERE result = 'SUCCESS') * 100.0 / COUNT(*), 2) as success_rate
FROM builds_my_project 
WHERE start_time >= NOW() - INTERVAL '30 days'
GROUP BY DATE(start_time)
ORDER BY build_date DESC;

-- Get average build duration by result
SELECT 
    result, 
    COUNT(*) as build_count,
    ROUND(AVG(duration_ms / 1000.0), 2) as avg_duration_seconds
FROM builds_my_project 
GROUP BY result;

-- Find builds with specific artifacts
SELECT build_number, build_url, artifacts
FROM builds_my_project 
WHERE artifacts ? 'artifact_0' 
  AND artifacts->'artifact_0'->>'filename' LIKE '%.jar';
```

### Example: Freestyle Job

In a freestyle job, you can access these variables in a shell build step:

```bash
#!/bin/bash
echo "Event Type: $POSTGRES_EVENT_TYPE"
echo "Table Name: $POSTGRES_TABLE_NAME"
echo "Event Data: $POSTGRES_EVENT_DATA"

# Parse JSON data (requires jq)
echo "$POSTGRES_EVENT_DATA" | jq '.'
```

### Example: Pipeline Job

In a pipeline job, access the variables through the `params` object:

```groovy
pipeline {
    agent any
    stages {
        stage('Process Database Event') {
            steps {
                script {
                    echo "Event Type: ${env.POSTGRES_EVENT_TYPE}"
                    echo "Table Name: ${env.POSTGRES_TABLE_NAME}"
                    echo "Event Data: ${env.POSTGRES_EVENT_DATA}"
                    
                    // Parse JSON in Groovy
                    def eventData = readJSON text: env.POSTGRES_EVENT_DATA
                    echo "Parsed data: ${eventData}"
                }
            }
        }
    }
}
```

## Architecture

### Components

- **SupabaseConnectionManager**: Singleton connection manager that pools WebSocket connections per Supabase instance
  - Shares one connection across all jobs for the same instance
  - Automatic reconnection with exponential backoff (1s → 30s)
  - Thread-safe event multiplexing to multiple subscribers
  - Connection health monitoring and statistics
- **SupabaseEventTrigger**: Main trigger class that subscribes to database events and schedules builds
- **SupabaseRealtimeClient**: WebSocket client for connecting to Supabase Realtime
- **SupabaseInstance**: Configuration object for Supabase instance details
- **PostgresEventTriggerConfiguration**: Global configuration for managing Supabase instances

### Event Flow

1. When a job with the trigger is started, the plugin uses the connection manager to subscribe to events
2. The connection manager creates or reuses an existing WebSocket connection to Supabase Realtime
3. Multiple jobs can share the same connection efficiently (connection pooling)
4. When a database event occurs, Supabase sends a message through the WebSocket
5. The connection manager routes events to all relevant job handlers (event multiplexing)
6. Each job receives the event, creates a build cause, and schedules a build
7. The event data is passed to the build as environment variables
8. If the connection drops, automatic reconnection with exponential backoff ensures reliability

### Production Features

#### Connection Pooling
- **One connection per Supabase instance** shared across all jobs
- Efficient resource usage (100 jobs = 1 connection, not 100)
- Reference counting manages connection lifecycle
- Automatic cleanup when last subscriber disconnects

#### Automatic Reconnection
- **Exponential backoff strategy**: 1s → 2s → 4s → 8s → 16s → max 30s
- Maximum 10 retry attempts before giving up
- Automatic resubscription to all channels after reconnect
- Network resilience ensures 24/7 reliability

#### Thread Safety
- `ConcurrentHashMap` for all shared data structures
- `AtomicInteger` for counters
- `CopyOnWriteArrayList` for subscription lists
- No race conditions or `ConcurrentModificationException`

#### Health Monitoring
- **Connection statistics API** for monitoring:
  - Connection state (CONNECTED, DISCONNECTED, CONNECTING, ERROR)
  - Uptime since connection established
  - Events received and failed
  - Reconnection attempts
  - Active subscriber count
- Detailed logging at all lifecycle stages
- Metrics integration for operational visibility

#### Architecture Diagram

**Before (Without Connection Manager):**
```
Job 1 ──► WebSocket 1 ──┐
Job 2 ──► WebSocket 2 ──┼──► Supabase
Job 3 ──► WebSocket 3 ──┘
```
*Issues: Multiple connections, no reconnection, not thread-safe*

**After (With Connection Manager):**
```
Job 1 ──┐
Job 2 ──┼──► Connection Manager ──► WebSocket (shared) ──► Supabase
Job 3 ──┘     (pooling, reconnection, multiplexing)
```
*Benefits: 1 connection, auto-reconnect, thread-safe, monitored*

## Production Readiness

### ✅ Enterprise Features
- **Connection Pooling**: Efficient resource usage with shared connections
- **Automatic Reconnection**: Network resilience with exponential backoff
- **Thread Safety**: Concurrent operations without race conditions
- **Health Monitoring**: Statistics API for operational visibility
- **Graceful Shutdown**: Proper cleanup on Jenkins shutdown
- **Backward Compatible**: Existing configurations work without changes

### Testing
- **21 unit tests** covering all core functionality
- **Functional testing** verified with INSERT/UPDATE/DELETE events
- **Thread safety** tested with concurrent operations
- **Memory leak prevention** verified

### Monitoring
Use the connection manager statistics API to monitor plugin health:
```java
// Access via Jenkins Script Console
import io.jenkins.plugins.supabase.SupabaseConnectionManager;

def stats = SupabaseConnectionManager.getInstance().getStats();
stats.each { instanceName, connStats ->
    println "Instance: ${instanceName}"
    println "  State: ${connStats.state}"
    println "  Uptime: ${(System.currentTimeMillis() - connStats.connectedSince) / 1000}s"
    println "  Events: ${connStats.eventsReceived} received, ${connStats.eventsFailed} failed"
    println "  Reconnections: ${connStats.reconnections}"
    println "  Subscribers: ${connStats.subscriberCount}"
}
```

**Recommended Alerts:**
- Alert if `state != CONNECTED` for > 5 minutes
- Alert if `reconnections > 3` in 1 hour
- Alert if `eventsFailed > threshold`

## Troubleshooting

### Connection Issues

- Verify that your Supabase URL is correct and includes the protocol (https:// or wss://)
- Ensure your API key credentials are correctly configured
- Check Jenkins logs for detailed error messages
- **Check connection statistics** via Script Console (see Monitoring section above)
- Connection will automatically retry with exponential backoff (check `reconnections` counter)

### No Events Received

- Verify that Realtime is enabled for your tables in Supabase
- Check that the table names are spelled correctly
- Ensure the schema is specified if not using the default "public" schema
- Verify that your API key has the necessary permissions
- **Check connection state** - should be `CONNECTED`
- Look for `eventsFailed` counter increases indicating processing errors

### Build Not Triggering

- Confirm that at least one event type (INSERT, UPDATE, or DELETE) is selected
- Check the Jenkins system log for trigger-related messages
- Verify that the job is enabled and not queued
- **Verify subscription** - check `subscriberCount` for the instance
- Test with a simple INSERT to confirm event delivery

## Development

### Building from Source

```bash
git clone https://github.com/radhakrisri/jenkins-supabase.git
cd jenkins-supabase
mvn clean package
```

### Quick Build Commands

```bash
# Build snapshot version
./release.sh build

# Run tests
./release.sh test

# Check current version
./release.sh version

# Create a release (requires clean git state)
./release.sh release 1.1.0
```

### Running Tests

```bash
mvn test
```

### Running in Development Mode

```bash
mvn hpi:run
```

This will start a Jenkins instance with the plugin loaded at http://localhost:8080/jenkins

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This plugin is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Support

For issues, questions, or contributions, please visit the [GitHub repository](https://github.com/radhakrisri/jenkins-supabase). Please use the repository's issue tracker to report any problems.

## Versioning and Releases

This plugin follows semantic versioning (SemVer) and uses git-changelist-maven-extension for automatic version management.

### Version Format

- **Release builds**: `X.Y.Z` (e.g., `1.1.0`)
- **Snapshot builds**: `X.Y.Z-SNAPSHOT` (e.g., `1.1.0-SNAPSHOT`)
- **Release candidates**: `X.Y.Z-RC.N` (e.g., `1.1.0-RC.1`)

### HPI File Naming

The generated HPI file follows Jenkins plugin naming conventions:
- **File name**: `jenkins-supabase.hpi` (version embedded in manifest)
- **ArtifactId**: `jenkins-supabase`
- **Display name**: "Jenkins Supabase Plugin"

### Release Process

1. **Development**: Work with SNAPSHOT versions (e.g., `1.1.0-SNAPSHOT`)
2. **Release**: Create a git tag with format `vX.Y.Z` (e.g., `v1.1.0`)
3. **Automatic versioning**: The git-changelist extension automatically:
   - Uses the tag version for release builds
   - Generates SNAPSHOT versions between releases
4. **Use release script**: Run `./release.sh release X.Y.Z` for automated releases

### Version Examples

```bash
# Check current version on untagged commit after v1.0.0
mvn help:evaluate -Dexpression=project.version -q -DforceStdout
# Output: 1.1.0-SNAPSHOT

# Tag and check version
git tag v1.1.0
mvn help:evaluate -Dexpression=project.version -q -DforceStdout  
# Output: 1.1.0

# Build artifacts
mvn clean package
# Generates: target/jenkins-supabase.hpi

# Manual version override
mvn clean package -Dchangelist=1.2.0-RC.1
# Generates: target/jenkins-supabase-1.2.0-RC.1.hpi
```

### Semantic Versioning Guidelines

- **Major (X.0.0)**: Breaking changes, incompatible API changes
- **Minor (x.Y.0)**: New features, backward compatible functionality
- **Patch (x.y.Z)**: Bug fixes, backward compatible fixes

### Best Practices

1. **Tag releases**: Always tag releases with `vX.Y.Z` format
2. **SNAPSHOT for development**: Keep SNAPSHOT suffix during development
3. **Clean git state**: Ensure working tree is clean before releases
4. **Test before release**: Run full test suite before tagging

## Acknowledgments

- Built using the [Jenkins Plugin Parent POM](https://github.com/jenkinsci/plugin-pom)
- Uses [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket) for WebSocket communication
- Integrates with [Supabase Realtime](https://supabase.com/docs/guides/realtime)
