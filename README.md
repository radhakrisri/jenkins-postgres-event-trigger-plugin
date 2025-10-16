# Jenkins Supabase Plugin

A comprehensive Jenkins plugin for Supabase integration that enables real-time event triggers, post-build actions, and database operations.

## Features

### Current Features
- **Real-time Event Monitoring**: Subscribe to INSERT, UPDATE, and DELETE events on Postgres tables
- **Multiple Supabase Instances**: Configure and manage multiple Supabase instances from Jenkins global configuration
- **Flexible Table Selection**: Monitor one or more tables per job, with support for schema specification
- **Event Data Access**: Event data is passed to builds as environment variables
- **Secure Credential Management**: Uses Jenkins credentials for API key storage

### Planned Features
- **Post-Build Actions**: Execute database operations after build completion
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

1. Create or configure a Jenkins job (Freestyle or Pipeline)
2. In the job configuration, under **Build Triggers**, check **Supabase Event Trigger**
3. Configure the trigger:
   - **Supabase Instance**: Select the instance to monitor
   - **Tables**: Enter comma-separated table names (e.g., `users, orders` or `public.users, myschema.orders`)
   - **Subscribe to Events**: Check the events you want to monitor (INSERT, UPDATE, DELETE)

> **Note**: Additional post-build actions and database operations will be available in future releases.

## Usage

### Accessing Event Data in Builds

When a build is triggered by a database event, the following environment variables are available:

- `POSTGRES_EVENT_TYPE`: The type of event (INSERT, UPDATE, or DELETE)
- `POSTGRES_TABLE_NAME`: The name of the table that triggered the event
- `POSTGRES_EVENT_DATA`: JSON string containing the full event payload from Supabase

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

- **PostgresEventTrigger**: Main trigger class that subscribes to database events and schedules builds
- **SupabaseRealtimeClient**: WebSocket client for connecting to Supabase Realtime
- **SupabaseInstance**: Configuration object for Supabase instance details
- **PostgresEventTriggerConfiguration**: Global configuration for managing Supabase instances

### Event Flow

1. When a job with the trigger is started, the plugin establishes a WebSocket connection to Supabase Realtime
2. The plugin subscribes to the specified tables and events
3. When a database event occurs, Supabase sends a message through the WebSocket
4. The plugin receives the event, creates a build cause, and schedules a build
5. The event data is passed to the build as environment variables

## Troubleshooting

### Connection Issues

- Verify that your Supabase URL is correct and includes the protocol (https:// or wss://)
- Ensure your API key credentials are correctly configured
- Check Jenkins logs for detailed error messages

### No Events Received

- Verify that Realtime is enabled for your tables in Supabase
- Check that the table names are spelled correctly
- Ensure the schema is specified if not using the default "public" schema
- Verify that your API key has the necessary permissions

### Build Not Triggering

- Confirm that at least one event type (INSERT, UPDATE, or DELETE) is selected
- Check the Jenkins system log for trigger-related messages
- Verify that the job is enabled and not queued

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

This plugin follows semantic versioning (SemVer) and uses git-changelist-maven-extension for automatic version management:

- **Development versions**: `X.Y.Z-SNAPSHOT` (e.g., `1.1.0-SNAPSHOT`)
- **Release versions**: `X.Y.Z` (e.g., `1.1.0`)
- **HPI file name**: Always `jenkins-supabase.hpi` (version embedded in manifest)

### Release Process

1. Development with SNAPSHOT versions
2. Create git tag with format `vX.Y.Z` for releases
3. Automatic version resolution via git-changelist extension
4. Use `./release.sh release X.Y.Z` for automated releases

See [VERSIONING.md](VERSIONING.md) for detailed versioning strategy.

## Acknowledgments

- Built using the [Jenkins Plugin Parent POM](https://github.com/jenkinsci/plugin-pom)
- Uses [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket) for WebSocket communication
- Integrates with [Supabase Realtime](https://supabase.com/docs/guides/realtime)
