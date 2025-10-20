# Fresh Environment Credentials

## Jenkins
- **URL**: http://localhost:8080
- **Admin Password**: `7c56e049313d450d821e2b0a8c146a38`
- **Docker Gateway IP**: `172.18.0.1` (for accessing host services from container)

## Supabase
- **API URL**: http://127.0.0.1:54321
- **Database URL**: postgresql://postgres:postgres@127.0.0.1:54322/postgres
- **Studio URL**: http://127.0.0.1:54323
- **Publishable Key**: `sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH`
- **Secret Key (Service Role)**: `sb_secret_N7UND0UgjKTVK-Uodkm0Hg_xSvEMPvz`

## Plugin Location
- **Source**: /home/rks/code/jenkins-supabase/target/jenkins-supabase.hpi
- **Size**: 1.6MB

## Manual Testing Steps

### Step 1: Complete Jenkins Initial Setup
1. Visit http://localhost:8080
2. Enter password: `7c56e049313d450d821e2b0a8c146a38`
3. Select "Install suggested plugins"
4. Wait for plugins to install
5. Create admin user or skip and continue as admin
6. Confirm Jenkins URL
7. Start using Jenkins

### Step 2: Install Plugin Dependencies
```bash
java -jar /tmp/jenkins-cli.jar -s http://localhost:8080 -auth admin:7c56e049313d450d821e2b0a8c146a38 install-plugin credentials plain-credentials structs
```

### Step 3: Install Supabase Plugin
```bash
sudo cp /home/rks/code/jenkins-supabase/target/jenkins-supabase.hpi ~/jenkins/jenkins_home/plugins/jenkins-supabase.jpi
cd ~/jenkins && docker compose restart jenkins
```

### Step 4: Create API Key Credential via Groovy
```groovy
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain
import jenkins.model.Jenkins
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl
import com.cloudbees.plugins.credentials.CredentialsScope

def jenkins = Jenkins.instance
def domain = Domain.global()
def store = jenkins.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore()

def secret = hudson.util.Secret.fromString('sb_secret_N7UND0UgjKTVK-Uodkm0Hg_xSvEMPvz')
def credentials = new StringCredentialsImpl(
    CredentialsScope.GLOBAL,
    'supabase-secret-key',
    'Supabase Service Role Key',
    secret
)

store.addCredentials(domain, credentials)
println "Created credential 'supabase-secret-key'"
```

### Step 5: Configure Supabase Instance via Groovy
```groovy
import io.jenkins.plugins.supabase.SupabaseEventTriggerConfiguration
import io.jenkins.plugins.supabase.SupabaseInstance

def config = SupabaseEventTriggerConfiguration.get()
def instances = config.getSupabaseInstances() ?: []

def newInstance = new SupabaseInstance(
    'local-dev',
    'http://172.18.0.1:54321',
    'supabase-secret-key',
    'postgresql://postgres:postgres@172.18.0.1:54322/postgres'
)

instances.add(newInstance)
config.setSupabaseInstances(instances)
config.save()

println "Configured Supabase instance 'local-dev'"
```

### Step 6: Setup Build Recorder Configuration via Groovy
```groovy
import io.jenkins.plugins.supabase.SupabaseEventTriggerConfiguration
import io.jenkins.plugins.supabase.SupabaseDataClient
import hudson.model.TaskListener
import java.io.PrintStream

def config = SupabaseEventTriggerConfiguration.get()
def instance = config.getInstanceByName('local-dev')

def listener = new TaskListener() {
    @Override
    PrintStream getLogger() {
        return System.out
    }
}

def dataClient = new SupabaseDataClient(instance, listener)

dataClient.createJobMetadataTable('public', 'jenkins_jobs')
dataClient.waitForSchemaCache()
dataClient.verifyTableExists('jenkins_jobs')

config.setBuildRecorderInstanceName('local-dev')
config.setBuildRecorderSchema('public')
config.setBuildRecorderJobTable('jenkins_jobs')
config.setBuildRecorderConfigured(true)
config.save()

println "Build Recorder configured successfully"
```

### Step 7: Verify Table in Supabase
```bash
psql postgresql://postgres:postgres@127.0.0.1:54322/postgres -c "\dt"
psql postgresql://postgres:postgres@127.0.0.1:54322/postgres -c "\d jenkins_jobs"
```

### Step 8: Create Test Job via CLI
Create file `test-job-config.xml`:
```xml
<?xml version='1.1' encoding='UTF-8'?>
<project>
  <description>Test job for Supabase Build Recorder</description>
  <builders>
    <hudson.tasks.Shell>
      <command>echo "Testing Supabase Build Recorder"
echo "Build: $BUILD_NUMBER"
sleep 2</command>
    </hudson.tasks.Shell>
  </builders>
  <publishers>
    <io.jenkins.plugins.supabase.SupabaseBuildRecorder>
      <instanceName>local-dev</instanceName>
      <recordArtifacts>true</recordArtifacts>
      <recordStages>true</recordStages>
      <recordTestResults>true</recordTestResults>
      <recordEnvironmentVariables>true</recordEnvironmentVariables>
    </io.jenkins.plugins.supabase.SupabaseBuildRecorder>
  </publishers>
</project>
```

Then create:
```bash
java -jar /tmp/jenkins-cli.jar -s http://localhost:8080 -auth admin:PASSWORD create-job test-supabase-recorder < test-job-config.xml
```

### Step 9: Run Build
```bash
java -jar /tmp/jenkins-cli.jar -s http://localhost:8080 -auth admin:PASSWORD build test-supabase-recorder -s -v
```

### Step 10: Verify Data in Supabase
```bash
# Check job metadata
psql postgresql://postgres:postgres@127.0.0.1:54322/postgres -c "SELECT * FROM jenkins_jobs;"

# Check build table
psql postgresql://postgres:postgres@127.0.0.1:54322/postgres -c "SELECT table_name FROM jenkins_jobs WHERE job_name = 'test-supabase-recorder';"

# Check build data
psql postgresql://postgres:postgres@127.0.0.1:54322/postgres -c "SELECT * FROM builds_test_supabase_recorder;"
```

## Expected Results
- ✅ jenkins_jobs table created automatically
- ✅ builds_test_supabase_recorder table created automatically
- ✅ Job metadata recorded
- ✅ Build data recorded
- ✅ Zero manual Supabase configuration required
