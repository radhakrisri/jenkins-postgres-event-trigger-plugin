# Example Jenkins Job Configuration

This directory contains examples of how to use the Postgres Event Trigger Plugin.

## Example 1: Basic Freestyle Job

### Job Setup

1. Create a new Freestyle project in Jenkins
2. Under "Build Triggers", check "Postgres/Supabase Event Trigger"
3. Configure:
   - **Supabase Instance**: my-production-instance
   - **Tables**: users
   - **Events**: Check INSERT and UPDATE

### Build Step (Execute Shell)

```bash
#!/bin/bash

echo "Event Type: $POSTGRES_EVENT_TYPE"
echo "Table Name: $POSTGRES_TABLE_NAME"
echo "Event Data: $POSTGRES_EVENT_DATA"

# Parse the JSON data using jq (install jq if not available)
if command -v jq &> /dev/null; then
    echo "Parsed Event Data:"
    echo "$POSTGRES_EVENT_DATA" | jq '.'
    
    # Extract specific fields
    RECORD_ID=$(echo "$POSTGRES_EVENT_DATA" | jq -r '.record.id // empty')
    if [ -n "$RECORD_ID" ]; then
        echo "Record ID: $RECORD_ID"
    fi
fi

# Your custom logic here
# For example, trigger a deployment, send a notification, etc.
```

## Example 2: Pipeline Job

### Jenkinsfile

```groovy
pipeline {
    agent any
    
    stages {
        stage('Process Database Event') {
            steps {
                script {
                    // Access environment variables
                    def eventType = env.POSTGRES_EVENT_TYPE
                    def tableName = env.POSTGRES_TABLE_NAME
                    def eventData = env.POSTGRES_EVENT_DATA
                    
                    echo "Processing ${eventType} event on table ${tableName}"
                    
                    // Parse JSON data
                    def jsonSlurper = new groovy.json.JsonSlurper()
                    def event = jsonSlurper.parseText(eventData)
                    
                    echo "Event details: ${event}"
                    
                    // Process based on event type
                    if (eventType == 'INSERT') {
                        handleInsert(event)
                    } else if (eventType == 'UPDATE') {
                        handleUpdate(event)
                    } else if (eventType == 'DELETE') {
                        handleDelete(event)
                    }
                }
            }
        }
        
        stage('Deploy or Notify') {
            steps {
                script {
                    echo "Executing deployment or notification logic..."
                    // Your deployment or notification logic here
                }
            }
        }
    }
}

def handleInsert(event) {
    echo "Handling INSERT event"
    echo "New record: ${event.record}"
    // Your custom logic for INSERT
}

def handleUpdate(event) {
    echo "Handling UPDATE event"
    echo "Old record: ${event.old_record}"
    echo "New record: ${event.record}"
    // Your custom logic for UPDATE
}

def handleDelete(event) {
    echo "Handling DELETE event"
    echo "Deleted record: ${event.old_record}"
    // Your custom logic for DELETE
}
```

## Example 3: Multiple Tables

### Configuration

Monitor multiple tables with different schemas:

- **Tables**: `public.users, public.orders, analytics.events`
- **Events**: INSERT, UPDATE, DELETE

### Build Step

```bash
#!/bin/bash

# Handle different tables differently
case "$POSTGRES_TABLE_NAME" in
    "users")
        echo "User table event detected"
        # User-specific logic
        ;;
    "orders")
        echo "Order table event detected"
        # Order-specific logic
        ;;
    "events")
        echo "Analytics event detected"
        # Analytics-specific logic
        ;;
    *)
        echo "Unknown table: $POSTGRES_TABLE_NAME"
        ;;
esac
```

## Example 4: Email Notification on Specific Events

### Jenkinsfile with Email Notification

```groovy
pipeline {
    agent any
    
    stages {
        stage('Process Event') {
            steps {
                script {
                    def jsonSlurper = new groovy.json.JsonSlurper()
                    def event = jsonSlurper.parseText(env.POSTGRES_EVENT_DATA)
                    
                    // Check if this is a high-priority event
                    if (event.record?.priority == 'high') {
                        currentBuild.description = "High priority ${env.POSTGRES_EVENT_TYPE} on ${env.POSTGRES_TABLE_NAME}"
                        
                        // Send email notification
                        emailext(
                            to: 'team@example.com',
                            subject: "High Priority Database Event: ${env.POSTGRES_EVENT_TYPE}",
                            body: """
                                A high priority database event has occurred:
                                
                                Event Type: ${env.POSTGRES_EVENT_TYPE}
                                Table: ${env.POSTGRES_TABLE_NAME}
                                Record ID: ${event.record?.id}
                                
                                Full Event Data:
                                ${env.POSTGRES_EVENT_DATA}
                            """
                        )
                    }
                }
            }
        }
    }
}
```

## Example 5: Conditional Build Logic

### Jenkinsfile with Conditions

```groovy
pipeline {
    agent any
    
    stages {
        stage('Validate Event') {
            steps {
                script {
                    def jsonSlurper = new groovy.json.JsonSlurper()
                    def event = jsonSlurper.parseText(env.POSTGRES_EVENT_DATA)
                    
                    // Only process events from specific users
                    if (event.record?.user_id in [1, 2, 3]) {
                        echo "Processing event for authorized user"
                    } else {
                        echo "Skipping event - unauthorized user"
                        currentBuild.result = 'NOT_BUILT'
                        return
                    }
                }
            }
        }
        
        stage('Execute Action') {
            when {
                expression { currentBuild.result != 'NOT_BUILT' }
            }
            steps {
                echo "Executing action based on validated event"
                // Your action here
            }
        }
    }
}
```

## Example 6: Integration with External Systems

### Jenkinsfile with HTTP Request

```groovy
pipeline {
    agent any
    
    stages {
        stage('Forward to External System') {
            steps {
                script {
                    def jsonSlurper = new groovy.json.JsonSlurper()
                    def event = jsonSlurper.parseText(env.POSTGRES_EVENT_DATA)
                    
                    // Forward event to external webhook
                    httpRequest(
                        httpMode: 'POST',
                        contentType: 'APPLICATION_JSON',
                        url: 'https://api.example.com/webhook',
                        requestBody: groovy.json.JsonOutput.toJson([
                            source: 'jenkins',
                            event_type: env.POSTGRES_EVENT_TYPE,
                            table: env.POSTGRES_TABLE_NAME,
                            data: event
                        ])
                    )
                }
            }
        }
    }
}
```

## Example 7: Slack Notification

### Jenkinsfile with Slack Integration

```groovy
pipeline {
    agent any
    
    stages {
        stage('Notify Slack') {
            steps {
                script {
                    def jsonSlurper = new groovy.json.JsonSlurper()
                    def event = jsonSlurper.parseText(env.POSTGRES_EVENT_DATA)
                    
                    def message = """
                        Database Event Detected!
                        Type: ${env.POSTGRES_EVENT_TYPE}
                        Table: ${env.POSTGRES_TABLE_NAME}
                        Record ID: ${event.record?.id ?: 'N/A'}
                    """
                    
                    slackSend(
                        color: getColorForEventType(env.POSTGRES_EVENT_TYPE),
                        message: message,
                        channel: '#database-events'
                    )
                }
            }
        }
    }
}

def getColorForEventType(eventType) {
    switch(eventType) {
        case 'INSERT':
            return 'good'  // green
        case 'UPDATE':
            return 'warning'  // yellow
        case 'DELETE':
            return 'danger'  // red
        default:
            return '#439FE0'  // blue
    }
}
```

## Tips for Production Use

1. **Idempotency**: Design your build logic to be idempotent, as events may be received multiple times
2. **Error Handling**: Always include error handling in your build scripts
3. **Logging**: Log event data for debugging and audit purposes
4. **Throttling**: Consider implementing rate limiting if you expect high event volumes
5. **Monitoring**: Monitor build queue and success rates
6. **Testing**: Test with non-production Supabase instances first

## Troubleshooting Examples

### Debug Event Data

```bash
#!/bin/bash

# Save event data to a file for inspection
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
EVENT_FILE="/tmp/postgres_event_${TIMESTAMP}.json"

echo "$POSTGRES_EVENT_DATA" > "$EVENT_FILE"
echo "Event data saved to: $EVENT_FILE"

# Pretty print if jq is available
if command -v jq &> /dev/null; then
    echo "Event data (formatted):"
    cat "$EVENT_FILE" | jq '.'
fi
```

### Verify Environment Variables

```groovy
pipeline {
    agent any
    stages {
        stage('Debug') {
            steps {
                script {
                    echo "=== Environment Variables ==="
                    echo "POSTGRES_EVENT_TYPE: ${env.POSTGRES_EVENT_TYPE}"
                    echo "POSTGRES_TABLE_NAME: ${env.POSTGRES_TABLE_NAME}"
                    echo "POSTGRES_EVENT_DATA: ${env.POSTGRES_EVENT_DATA}"
                    echo "==========================="
                }
            }
        }
    }
}
```
