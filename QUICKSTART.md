# Quick Start Guide

Get started with the Postgres Event Trigger Plugin in 5 minutes!

## Prerequisites

- Jenkins 2.361.4+ installed and running
- A Supabase project with Realtime enabled
- Supabase API key (anon or service role)

## Step 1: Install the Plugin

1. Download the latest `postgres-event-trigger.hpi` file from the releases page
2. In Jenkins, go to **Manage Jenkins** → **Manage Plugins** → **Advanced**
3. Under "Upload Plugin", choose the `.hpi` file
4. Click **Upload**
5. Restart Jenkins when prompted

## Step 2: Configure Credentials

1. Go to **Manage Jenkins** → **Manage Credentials**
2. Click on **(global)** domain
3. Click **Add Credentials**
4. Configure:
   - **Kind**: Secret text
   - **Scope**: Global
   - **Secret**: Paste your Supabase API key
   - **ID**: `supabase-api-key` (or any identifier)
   - **Description**: Supabase API Key
5. Click **OK**

## Step 3: Configure Supabase Instance

1. Go to **Manage Jenkins** → **Configure System**
2. Scroll to **Postgres/Supabase Event Trigger Configuration**
3. Click **Add** under Supabase Instances
4. Configure:
   - **Instance Name**: `my-supabase` (unique identifier)
   - **Supabase URL**: `https://xxxxx.supabase.co` (your project URL)
   - **API Key Credentials**: Select the credential you created in Step 2
5. Click **Save**

## Step 4: Enable Realtime in Supabase

1. Go to your Supabase project dashboard
2. Navigate to **Database** → **Replication**
3. Enable Realtime for the tables you want to monitor
4. Example SQL to enable Realtime:
   ```sql
   -- Enable Realtime for the 'users' table
   alter publication supabase_realtime add table users;
   ```

## Step 5: Create a Test Job

1. In Jenkins, click **New Item**
2. Enter job name (e.g., "Database Event Test")
3. Select **Freestyle project**
4. Click **OK**

## Step 6: Configure the Trigger

1. In the job configuration, scroll to **Build Triggers**
2. Check **Postgres/Supabase Event Trigger**
3. Configure:
   - **Supabase Instance**: `my-supabase`
   - **Tables**: `users` (or your table name)
   - **Subscribe to Events**: Check **INSERT**, **UPDATE**, **DELETE**
4. Scroll to **Build** section
5. Click **Add build step** → **Execute shell**
6. Enter this script:
   ```bash
   #!/bin/bash
   echo "Event Type: $POSTGRES_EVENT_TYPE"
   echo "Table Name: $POSTGRES_TABLE_NAME"
   echo "Event Data: $POSTGRES_EVENT_DATA"
   ```
7. Click **Save**

## Step 7: Test the Trigger

1. In your Supabase SQL Editor, insert a test record:
   ```sql
   INSERT INTO users (name, email) VALUES ('Test User', 'test@example.com');
   ```

2. Go back to Jenkins and check your job
3. You should see a new build triggered automatically!
4. Click on the build number to view the console output
5. You should see the event data printed

## Verify Everything Works

✅ **Successful Setup Indicators:**
- Build triggered automatically after database event
- Environment variables contain event data
- Console output shows correct event type and table name
- Event data is valid JSON

❌ **Troubleshooting:**
- **No build triggered**: Check Jenkins logs, verify Realtime is enabled, check table name
- **Connection errors**: Verify Supabase URL and API key credentials
- **Empty event data**: Ensure Realtime is properly configured for the table

## Next Steps

### Learn More
- Read the full [README.md](README.md) for detailed documentation
- Check [examples/README.md](examples/README.md) for advanced use cases
- Review [IMPLEMENTATION.md](IMPLEMENTATION.md) for technical details

### Try These Examples
1. **Monitor Multiple Tables**: Add more tables separated by commas
2. **Filter Events**: Only subscribe to specific event types
3. **Parse Event Data**: Use `jq` or Groovy to extract specific fields
4. **Send Notifications**: Integrate with Slack, email, or other services

### Common Patterns

#### Pattern 1: Deploy on Database Change
```bash
#!/bin/bash
if [ "$POSTGRES_EVENT_TYPE" = "UPDATE" ]; then
  echo "Deploying application..."
  ./deploy.sh
fi
```

#### Pattern 2: Send Slack Notification
```groovy
pipeline {
    agent any
    stages {
        stage('Notify') {
            steps {
                slackSend(
                    message: "Database event: ${env.POSTGRES_EVENT_TYPE} on ${env.POSTGRES_TABLE_NAME}",
                    channel: '#deployments'
                )
            }
        }
    }
}
```

#### Pattern 3: Conditional Processing
```bash
#!/bin/bash
# Only process events from specific schema
if [ "$POSTGRES_TABLE_NAME" = "users" ]; then
  echo "Processing user event..."
  # Your logic here
fi
```

## Security Best Practices

1. **Use Service Role Keys Carefully**: Service role keys have elevated permissions
2. **Enable Row Level Security**: Protect sensitive data in Supabase
3. **Limit Credential Access**: Only give Jenkins administrators access to credentials
4. **Use Separate Instances**: Use different Supabase projects for dev/staging/prod
5. **Monitor Build Logs**: Regularly check for unusual activity

## Support

Need help? Check these resources:

- **Documentation**: [README.md](README.md)
- **Examples**: [examples/README.md](examples/README.md)
- **Issues**: [GitHub Issues](https://github.com/radhakrisri/jenkins-postgres-event-trigger-plugin/issues)
- **Contributing**: [CONTRIBUTING.md](CONTRIBUTING.md)

## Quick Reference

### Environment Variables Available in Builds
- `POSTGRES_EVENT_TYPE`: INSERT, UPDATE, or DELETE
- `POSTGRES_TABLE_NAME`: Name of the table
- `POSTGRES_EVENT_DATA`: JSON event payload

### Event Payload Structure
```json
{
  "type": "INSERT",
  "schema": "public",
  "table": "users",
  "record": { /* new record data */ },
  "old_record": { /* old record data (UPDATE/DELETE only) */ }
}
```

### Useful Commands

**Test Supabase Connection:**
```bash
curl -X GET "https://xxxxx.supabase.co/rest/v1/" \
  -H "apikey: YOUR_API_KEY"
```

**Enable Realtime for Table:**
```sql
alter publication supabase_realtime add table your_table_name;
```

**Check Realtime Status:**
```sql
select * from pg_publication_tables where pubname = 'supabase_realtime';
```

---

**Congratulations!** You've successfully set up the Postgres Event Trigger Plugin. Your Jenkins jobs will now automatically respond to database changes! 🎉
