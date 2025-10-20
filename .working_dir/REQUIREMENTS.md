# Jenkins Supabase Plugin - Requirements & Workflow

## Overview
This document defines the expected workflow and requirements for the Jenkins Supabase Build Recorder plugin.

## User Workflow

### Step 1: Configure Supabase Instances
**Location**: Jenkins → Manage Jenkins → Configure System → Supabase Event Trigger Configuration

**User Actions**:
- Add one or more Supabase instances with:
  - Instance Name (unique identifier)
  - API URL
  - Publishable Key (anon key)
  - Service Role Key (credentials)
- Save the configuration

**Plugin Behavior**:
- Store instance configurations
- Validate URLs and credentials
- Make instances available for selection in Build Recorder Configuration

### Step 2: Configure Build Recorder (CRITICAL NEW WORKFLOW)
**Location**: Jenkins → Manage Jenkins → Configure System → Supabase Build Recorder Configuration

**User Actions**:
- Select the Supabase instance where build data will be stored
- Specify the schema to use (e.g., "public")
- Specify the table name for job metadata (e.g., "jenkins_jobs")
- Click "Setup and Verify" button

**Plugin Behavior**:
- **IMPORTANT**: User does NO manual configuration on Supabase
- When "Setup and Verify" is clicked:
  1. Create the job metadata table in the specified schema
  2. Create necessary database views/functions
  3. Wait until PostgREST schema cache refreshes (critical for avoiding race conditions)
  4. Verify everything is ready
  5. ONLY THEN return control to Jenkins
- Display success/error message
- Store the configuration (instance, schema, table name)
- Enable Build Recorder section in Job configurations

**When NOT Configured**:
- Build Recorder section in Job configurations must be DISABLED
- Show appropriate message: "Build Recorder Configuration is not set up. Please configure in Jenkins Global Settings first."

### Step 3: Configure Individual Jobs
**Location**: Job → Configure → Post-build Actions → Record Build Data to Supabase

**User Actions**:
- Add "Record Build Data to Supabase" post-build action
- Select which data to record (artifacts, stages, test results, etc.)
- Optionally add custom fields as JSON
- Save job configuration

**Plugin Behavior**:
- When job is saved:
  1. Create a build-specific table: `builds_{job_path}` in the same schema
  2. Table name derived from job path (e.g., "builds_my_job", "builds_folder_my_job")
  3. Wait for table creation to complete
  4. Register job in the job metadata table
  5. Return control to Jenkins

**When Build Recorder Configuration NOT Set**:
- Build Recorder section must be disabled
- Show message: "Build Recorder is not configured. Please configure it in Jenkins Global Settings."

### Step 4: Build Execution
**User Actions**:
- Trigger builds normally

**Plugin Behavior**:
- After each build completes:
  1. Collect build data (number, result, duration, timestamp, etc.)
  2. Optionally collect artifacts, stages, test results, environment variables
  3. Record all data to the job's build table (`builds_{job_path}`)
  4. Handle errors gracefully without failing the build

## Key Requirements

### Database Management
1. **Job Metadata Table** (`jenkins_jobs` or user-specified name):
   - Created during Build Recorder Configuration setup
   - Stores: job_name, job_path, table_name, created_at, updated_at
   
2. **Build Tables** (`builds_{job_path}`):
   - Created when job is configured with Build Recorder
   - One table per job
   - Stores: build_number, result, duration_ms, start_time, end_time, and optional data

### Race Condition Prevention
- **CRITICAL**: After creating tables/views, wait for PostgREST schema cache to refresh
- Recommended wait time: 3-5 seconds
- Without this wait, immediate builds may fail with "relation does not exist" errors

### Error Handling
1. **JavaScript/Jelly** (Frontend):
   - Validate all HTTP responses before parsing JSON
   - Validate data structure before accessing properties
   - Provide fallback values for undefined properties
   - Display clear error messages (never "undefined")

2. **Java** (Backend):
   - Wrap all HTTP response handlers in try-catch
   - Always return valid JSON responses
   - Log errors with context
   - Never let exceptions propagate without handling

### User Experience
1. **Configuration State**:
   - Clear indication when Build Recorder Configuration is not set up
   - Disable Job-level Build Recorder section until global config is complete
   - Show helpful messages guiding users to configure

2. **Validation**:
   - Validate instance selection
   - Validate schema and table names
   - Test connectivity before allowing save
   - Provide immediate feedback on errors

3. **Zero Manual Supabase Configuration**:
   - Users should NEVER need to manually create tables, views, or functions
   - Plugin handles ALL database setup automatically
   - Plugin waits for all changes to propagate before returning

## Files to Modify

### Java Files
1. **SupabaseEventTriggerConfiguration.java**
   - Add Build Recorder Configuration fields (instance, schema, table)
   - Add `doSetupAndVerify` method for initialization
   - Add error handling wrapper
   - Add schema cache wait logic

2. **SupabaseBuildRecorder.java**
   - Check if Build Recorder Configuration is set
   - Disable if not configured
   - Show appropriate messages

3. **SupabaseDataClient.java**
   - Implement table creation logic
   - Implement schema cache waiting
   - Add build data recording methods

### Jelly Files
1. **SupabaseEventTriggerConfiguration/config.jelly**
   - Add Build Recorder Configuration section
   - Add Setup and Verify button
   - Add JavaScript error handling with validation
   - Add fallback values

2. **SupabaseBuildRecorder/config.jelly**
   - Check configuration state
   - Show disabled state with message when not configured
   - Show configuration options when enabled

## Testing Checklist

### Clean State Test
- [ ] Start with fresh Jenkins and Supabase
- [ ] Configure Supabase instance
- [ ] Configure Build Recorder Configuration
- [ ] Verify job metadata table created
- [ ] Verify no errors in JavaScript/Java
- [ ] Create test job with Build Recorder
- [ ] Verify build table created
- [ ] Trigger build immediately (test race condition fix)
- [ ] Verify build data recorded

### Error Handling Test
- [ ] Test with invalid Supabase URL
- [ ] Test with invalid credentials
- [ ] Test with network failures
- [ ] Verify clear error messages (no "undefined")
- [ ] Verify JSON responses always valid

### Workflow Test
- [ ] Verify Build Recorder disabled without configuration
- [ ] Configure Build Recorder Configuration
- [ ] Verify Build Recorder enabled
- [ ] Reset configuration
- [ ] Verify Build Recorder disabled again

## Current Status

**Files Reviewed**:
- ✅ SupabaseEventTriggerConfiguration.java - Manages Supabase instances
- ✅ SupabaseBuildRecorder.java - Job-level build recorder

**Missing Implementation**:
- ❌ Build Recorder Configuration in GlobalConfiguration
- ❌ Setup and Verify button handler
- ❌ Job metadata table creation
- ❌ Build table creation on job save
- ❌ Schema cache wait logic
- ❌ Conditional enabling/disabling of Build Recorder in jobs
- ❌ Frontend error handling improvements

**Next Steps**:
1. Review SupabaseDataClient.java to understand existing table creation
2. Add Build Recorder Configuration to SupabaseEventTriggerConfiguration
3. Implement doSetupAndVerify with proper error handling
4. Add schema cache wait logic
5. Update Jelly files with validation and conditional display
6. Test end-to-end workflow
