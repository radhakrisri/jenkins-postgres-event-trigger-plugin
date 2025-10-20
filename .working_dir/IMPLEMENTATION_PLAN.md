# Implementation Plan - Jenkins Supabase Build Recorder

## Current State Analysis

### What Exists
1. **SupabaseEventTriggerConfiguration.java**
   - Manages list of Supabase instances
   - Provides validation for URLs and names
   - Has methods to get instances by name

2. **SupabaseBuildRecorder.java**
   - Job-level post-build action
   - Records build data to Supabase
   - Currently calls `dataClient.initializeTables()` directly

3. **SupabaseDataClient.java**
   - Has methods to register job metadata and record build data
   - Uses REST API to insert/upsert records
   - Assumes tables already exist (see comment: "Tables must be created manually")

### What's Missing (Per Requirements)

1. **Build Recorder Configuration** (Global Jenkins Settings)
   - Schema selection field
   - Job metadata table name field
   - "Setup and Verify" button
   - Initialization logic to create tables
   - Schema cache wait logic

2. **Conditional Enabling of Build Recorder**
   - Build Recorder disabled until global config is set
   - Message shown when not configured

3. **Automatic Table Creation**
   - Job metadata table creation
   - Build tables creation on job save
   - Views/functions creation
   - Wait for PostgREST schema cache refresh

4. **Robust Error Handling**
   - Frontend validation (no "undefined" errors)
   - Backend exception wrapping
   - Always return valid JSON

## Implementation Steps

### Phase 1: Add Build Recorder Configuration to GlobalConfiguration

**File**: `SupabaseEventTriggerConfiguration.java`

**Add Fields**:
```java
private String buildRecorderInstanceName;      // Selected Supabase instance
private String buildRecorderSchema = "public"; // Schema name (default: public)
private String buildRecorderJobTable = "jenkins_jobs"; // Job metadata table
private boolean buildRecorderConfigured = false; // Flag indicating setup complete
```

**Add Methods**:
```java
// Getters/setters for new fields

// Handler for "Setup and Verify" button
public HttpResponse doSetupAndVerify(
    @QueryParameter String instanceName,
    @QueryParameter String schema,
    @QueryParameter String jobTable
) {
    try {
        return performSetupAndVerify(instanceName, schema, jobTable);
    } catch (Exception e) {
        LOGGER.log(Level.SEVERE, "Unexpected error in doSetupAndVerify", e);
        return jsonResponse("error", "Unexpected error: " + e.getMessage());
    }
}

private HttpResponse performSetupAndVerify(...) {
    // 1. Validate instance exists
    // 2. Get SupabaseDataClient
    // 3. Create job metadata table
    // 4. Create necessary views/functions
    // 5. Wait 3-5 seconds for schema cache refresh
    // 6. Verify table exists via REST API
    // 7. Set buildRecorderConfigured = true
    // 8. Save configuration
    // 9. Return success JSON
}

// Helper to create JSON responses
private HttpResponse jsonResponse(String kind, String message) {
    return HttpResponses.html(
        "{\"kind\":\"" + kind + "\",\"message\":\"" + message + "\"}"
    );
}
```

### Phase 2: Update SupabaseDataClient for Table Creation

**File**: `SupabaseDataClient.java`

**Add Methods**:
```java
/**
 * Create the job metadata table in Supabase.
 * Called during Build Recorder Configuration setup.
 */
public void createJobMetadataTable(String schema, String tableName) throws IOException {
    // Use Supabase REST API to execute SQL
    // CREATE TABLE IF NOT EXISTS schema.tableName (...)
    // Add indexes, constraints, etc.
}

/**
 * Create a build-specific table for a job.
 * Called when job is configured with Build Recorder.
 */
public void createBuildTable(String schema, String tableName) throws IOException {
    // CREATE TABLE IF NOT EXISTS schema.tableName (...)
    // Columns: build_number, result, duration_ms, start_time, etc.
}

/**
 * Wait for PostgREST schema cache to refresh.
 * CRITICAL to prevent "relation does not exist" errors.
 */
public void waitForSchemaCache() throws InterruptedException {
    Thread.sleep(3000); // 3 seconds
}

/**
 * Verify table exists by querying it via REST API.
 */
public boolean verifyTableExists(String tableName) {
    try {
        // HEAD request to /rest/v1/{tableName}
        // Return true if 200, false if 404
    } catch (Exception e) {
        return false;
    }
}
```

### Phase 3: Update config.jelly for Build Recorder Configuration

**File**: `SupabaseEventTriggerConfiguration/config.jelly`

**Add Section** (after Supabase Instances section):
```xml
<f:section title="Build Recorder Configuration">
    <f:entry title="Supabase Instance" field="buildRecorderInstanceName">
        <f:select/>
        <f:description>Select the Supabase instance where build data will be stored</f:description>
    </f:entry>
    
    <f:entry title="Schema" field="buildRecorderSchema">
        <f:textbox default="public"/>
        <f:description>Database schema to use (default: public)</f:description>
    </f:entry>
    
    <f:entry title="Job Metadata Table" field="buildRecorderJobTable">
        <f:textbox default="jenkins_jobs"/>
        <f:description>Table name for storing job metadata</f:description>
    </f:entry>
    
    <f:validateButton title="Setup and Verify" 
                      progress="Setting up tables..." 
                      method="setupAndVerify" 
                      with="buildRecorderInstanceName,buildRecorderSchema,buildRecorderJobTable"/>
    
    <f:entry title="">
        <div id="setupResult"></div>
    </f:entry>
</f:section>

<script type="text/javascript">
// Add JavaScript for handling Setup and Verify response
// MUST include error validation to prevent "undefined" errors
</script>
```

**JavaScript with Robust Error Handling**:
```javascript
function handleSetupResponse(response) {
    try {
        // Validate HTTP response
        if (!response.ok) {
            throw new Error('HTTP error! status: ' + response.status);
        }
        
        return response.json().then(function(data) {
            // Validate data structure
            if (!data || typeof data !== 'object') {
                throw new Error('Invalid response format from server');
            }
            
            // Extract with fallbacks
            var kind = data.kind || 'error';
            var message = data.message || 'Unknown response from server';
            
            // Display message
            var resultDiv = document.getElementById('setupResult');
            if (kind === 'ok') {
                resultDiv.innerHTML = '<div class="ok">' + message + '</div>';
            } else {
                resultDiv.innerHTML = '<div class="error">' + message + '</div>';
            }
        });
    } catch (error) {
        var errorMessage = error.message || 'Unknown error occurred';
        document.getElementById('setupResult').innerHTML = 
            '<div class="error">Error: ' + errorMessage + '</div>';
    }
}
```

### Phase 4: Update SupabaseBuildRecorder to Check Configuration

**File**: `SupabaseBuildRecorder.java`

**Modify Descriptor**:
```java
@Override
public boolean isApplicable(Class<? extends AbstractProject> jobType) {
    // Check if Build Recorder Configuration is set up
    SupabaseEventTriggerConfiguration config = SupabaseEventTriggerConfiguration.get();
    return config != null && config.isBuildRecorderConfigured();
}

public FormValidation doCheckConfiguration() {
    SupabaseEventTriggerConfiguration config = SupabaseEventTriggerConfiguration.get();
    if (config == null || !config.isBuildRecorderConfigured()) {
        return FormValidation.error(
            "Build Recorder is not configured. Please configure it in " +
            "Manage Jenkins → Configure System → Supabase Build Recorder Configuration first."
        );
    }
    return FormValidation.ok();
}
```

**File**: `SupabaseBuildRecorder/config.jelly`

**Add Conditional Display**:
```xml
<j:choose>
    <j:when test="${descriptor.isBuildRecorderConfigured()}">
        <!-- Show normal configuration options -->
    </j:when>
    <j:otherwise>
        <div class="warning">
            <strong>Build Recorder is not configured.</strong>
            Please configure it in Manage Jenkins → Configure System → 
            Supabase Build Recorder Configuration first.
        </div>
    </j:otherwise>
</j:choose>
```

### Phase 5: Update initializeTables to Create Build Table

**File**: `SupabaseBuildRecorder.java` → `perform()` method

**Before Recording**:
```java
// Get Build Recorder Configuration
SupabaseEventTriggerConfiguration config = SupabaseEventTriggerConfiguration.get();
String schema = config.getBuildRecorderSchema();

// Ensure build table exists for this job
dataClient.ensureBuildTableExists(schema, run.getParent());
```

**File**: `SupabaseDataClient.java`

**Add Method**:
```java
public void ensureBuildTableExists(String schema, Job<?, ?> job) throws IOException {
    String tableName = generateTableName(job);
    
    // Check if table exists (could cache this check)
    if (!verifyTableExists(tableName)) {
        createBuildTable(schema, tableName);
        
        // Register in job metadata
        registerJobMetadata(job, tableName);
        
        // CRITICAL: Wait for schema cache
        try {
            waitForSchemaCache();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for schema cache", e);
        }
    }
}
```

## Testing Plan

### Test 1: Fresh Installation
1. Start fresh Jenkins + Supabase
2. Install plugin
3. Configure Supabase instance
4. **Verify**: Build Recorder section shows "not configured" message
5. Navigate to Build Recorder Configuration
6. Fill in instance, schema, table name
7. Click "Setup and Verify"
8. **Verify**: Success message appears
9. **Verify**: Job metadata table created in Supabase
10. **Verify**: No JavaScript errors, no "undefined" messages

### Test 2: Job Configuration
1. Create new freestyle job
2. **Verify**: Build Recorder post-build action is available (not disabled)
3. Add Build Recorder post-build action
4. Save job
5. **Verify**: Build table created in Supabase
6. **Verify**: Job registered in job metadata table

### Test 3: Build Execution (Race Condition Test)
1. Create new job
2. Add Build Recorder
3. Save job
4. **IMMEDIATELY** trigger build
5. **Verify**: Build completes successfully
6. **Verify**: Build data recorded in build table
7. **Verify**: No "relation does not exist" errors

### Test 4: Error Handling
1. Try Setup with invalid instance
2. **Verify**: Clear error message (not "undefined")
3. Try Setup with network disconnected
4. **Verify**: Clear error message
5. Try accessing Build Recorder without configuration
6. **Verify**: Appropriate disabled/warning message

## Files Summary

### Java Files to Modify
1. `SupabaseEventTriggerConfiguration.java` - Add Build Recorder Configuration
2. `SupabaseDataClient.java` - Add table creation methods
3. `SupabaseBuildRecorder.java` - Add configuration check

### Jelly Files to Modify
1. `SupabaseEventTriggerConfiguration/config.jelly` - Add UI for Build Recorder Configuration
2. `SupabaseBuildRecorder/config.jelly` - Add conditional display

### New Files
None - all changes are modifications

## Priority Order

1. **HIGH**: SupabaseDataClient.java - Table creation methods (foundational)
2. **HIGH**: SupabaseEventTriggerConfiguration.java - Add fields and doSetupAndVerify
3. **HIGH**: SupabaseEventTriggerConfiguration/config.jelly - Add UI with error handling
4. **MEDIUM**: SupabaseBuildRecorder.java - Configuration check
5. **MEDIUM**: SupabaseBuildRecorder/config.jelly - Conditional display
6. **LOW**: Documentation updates

## Next Steps

1. Implement table creation in SupabaseDataClient
2. Add Build Recorder Configuration to SupabaseEventTriggerConfiguration
3. Update config.jelly with robust JavaScript
4. Test end-to-end workflow
5. Document setup process
