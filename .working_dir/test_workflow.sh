#!/bin/bash

# Jenkins Supabase Plugin - End-to-End Testing Script
# This script automates the complete testing workflow

set -e  # Exit on error

# Configuration
JENKINS_URL="http://localhost:8080"
JENKINS_USER="admin"
JENKINS_PASSWORD="45009658f48849db9605bc386404fc41"
SUPABASE_API_URL="http://127.0.0.1:54321"
SUPABASE_DB_URL="postgresql://postgres:postgres@127.0.0.1:54322/postgres"
SUPABASE_API_KEY="sb_secret_N7UND0UgjKTVK-Uodkm0Hg_xSvEMPvz"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}=== Jenkins Supabase Plugin End-to-End Testing ===${NC}\n"

# Step 1: Check Jenkins is running
echo -e "${YELLOW}[1/8] Checking Jenkins availability...${NC}"
if curl -s -o /dev/null -w "%{http_code}" "$JENKINS_URL/login" | grep -q "200"; then
    echo -e "${GREEN}✓ Jenkins is running${NC}\n"
else
    echo -e "${RED}✗ Jenkins is not accessible${NC}"
    exit 1
fi

# Step 2: Check Supabase is running
echo -e "${YELLOW}[2/8] Checking Supabase availability...${NC}"
if curl -s -o /dev/null -w "%{http_code}" "$SUPABASE_API_URL" | grep -q "200"; then
    echo -e "${GREEN}✓ Supabase is running${NC}\n"
else
    echo -e "${RED}✗ Supabase is not accessible${NC}"
    exit 1
fi

# Step 3: Check if plugin is installed
echo -e "${YELLOW}[3/8] Checking if plugin is installed...${NC}"
if [ -f ~/jenkins/jenkins_home/plugins/jenkins-supabase.hpi ]; then
    echo -e "${GREEN}✓ Plugin file found: $(ls -lh ~/jenkins/jenkins_home/plugins/jenkins-supabase.hpi | awk '{print $9, "("$5")"}')${NC}\n"
else
    echo -e "${RED}✗ Plugin file not found${NC}"
    exit 1
fi

# Step 4: Wait for Jenkins to be ready
echo -e "${YELLOW}[4/8] Waiting for Jenkins to be fully ready...${NC}"
for i in {1..30}; do
    if curl -s -u "$JENKINS_USER:$JENKINS_PASSWORD" "$JENKINS_URL/api/json" >/dev/null 2>&1; then
        echo -e "${GREEN}✓ Jenkins is ready${NC}\n"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}✗ Jenkins did not become ready in time${NC}"
        echo -e "${YELLOW}Note: You may need to complete the initial setup wizard manually${NC}"
        echo -e "${YELLOW}Visit: $JENKINS_URL${NC}\n"
        exit 1
    fi
    echo -n "."
    sleep 2
done

# Step 5: Check if we can access Jenkins API
echo -e "${YELLOW}[5/8] Testing Jenkins API access...${NC}"
CRUMB=$(curl -s -u "$JENKINS_USER:$JENKINS_PASSWORD" "$JENKINS_URL/crumbIssuer/api/json" 2>/dev/null | grep -o '"crumb":"[^"]*"' | cut -d'"' -f4 || echo "")
if [ -n "$CRUMB" ]; then
    echo -e "${GREEN}✓ Jenkins API is accessible (CSRF token obtained)${NC}\n"
else
    echo -e "${YELLOW}⚠ Could not get CSRF token - Jenkins may need initial setup${NC}"
    echo -e "${YELLOW}Please complete the setup wizard at: $JENKINS_URL${NC}\n"
fi

# Step 6: Verify Supabase database connectivity
echo -e "${YELLOW}[6/8] Testing Supabase database connectivity...${NC}"
if command -v psql >/dev/null 2>&1; then
    if psql "$SUPABASE_DB_URL" -c "SELECT version();" >/dev/null 2>&1; then
        echo -e "${GREEN}✓ Database connection successful${NC}\n"
    else
        echo -e "${YELLOW}⚠ Could not connect to database (psql available but connection failed)${NC}\n"
    fi
else
    echo -e "${YELLOW}⚠ psql not installed, skipping direct database test${NC}\n"
fi

# Step 7: Check Supabase Studio
echo -e "${YELLOW}[7/8] Checking Supabase Studio...${NC}"
if curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:54323" | grep -q "200"; then
    echo -e "${GREEN}✓ Supabase Studio is accessible at http://127.0.0.1:54323${NC}\n"
else
    echo -e "${YELLOW}⚠ Supabase Studio may not be ready${NC}\n"
fi

# Step 8: Create manual testing guide
echo -e "${YELLOW}[8/8] Creating manual testing checklist...${NC}"

cat > /home/rks/code/jenkins-supabase/.working_dir/TESTING_CHECKLIST.md << 'EOF'
# End-to-End Testing Checklist

## Environment Status
- ✓ Jenkins: http://localhost:8080
- ✓ Supabase API: http://127.0.0.1:54321
- ✓ Supabase Studio: http://127.0.0.1:54323
- ✓ Database: postgresql://postgres:postgres@127.0.0.1:54322/postgres

## Credentials
- **Jenkins Admin Password**: `45009658f48849db9605bc386404fc41`
- **Supabase API Key (Secret)**: `sb_secret_N7UND0UgjKTVK-Uodkm0Hg_xSvEMPvz`
- **Supabase Publishable Key**: `sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH`

## Testing Steps

### 1. Initial Jenkins Setup (if needed)
- [ ] Visit http://localhost:8080
- [ ] Enter admin password: `45009658f48849db9605bc386404fc41`
- [ ] Select "Install suggested plugins" or "Select plugins to install"
- [ ] Create first admin user or continue as admin
- [ ] Complete setup wizard

### 2. Configure API Key Credentials in Jenkins
- [ ] Go to **Manage Jenkins** > **Credentials** > **System** > **Global credentials**
- [ ] Click **Add Credentials**
- [ ] Select **Secret text** as Kind
- [ ] Secret: `sb_secret_N7UND0UgjKTVK-Uodkm0Hg_xSvEMPvz`
- [ ] ID: `supabase-secret-key`
- [ ] Description: `Supabase Service Role Key`
- [ ] Click **OK**

### 3. Configure Supabase Instance
- [ ] Go to **Manage Jenkins** > **System**
- [ ] Scroll to **Supabase** section
- [ ] Click **Add** under Supabase Instances
- [ ] Fill in:
  - Instance Name: `local-dev`
  - Supabase URL: `http://127.0.0.1:54321`
  - API Key Credentials: Select `supabase-secret-key`
  - Database URL: `postgresql://postgres:postgres@127.0.0.1:54322/postgres`
- [ ] Click **Save**

### 4. Setup Build Recorder Configuration
- [ ] Go to **Manage Jenkins** > **System**
- [ ] Scroll to **Build Recorder Configuration** section
- [ ] Verify fields are present:
  - Supabase Instance dropdown
  - Schema field (should show "public")
  - Job Metadata Table field (should show "jenkins_jobs")
  - "Setup and Verify" button
- [ ] Select Instance: `local-dev`
- [ ] Click **Setup and Verify** button
- [ ] **Expected Result**: Success message appears
- [ ] Verify status shows: "✓ Build Recorder is configured and ready to use."
- [ ] Click **Save**

### 5. Verify Table Creation in Supabase
- [ ] Open Supabase Studio: http://127.0.0.1:54323
- [ ] Go to **Table Editor**
- [ ] Verify `jenkins_jobs` table exists in `public` schema
- [ ] Check table structure has columns:
  - job_name
  - job_full_name (UNIQUE constraint)
  - job_display_name
  - table_name
  - job_type
  - job_url
  - folder_path
  - configuration (JSONB)
  - created_at
  - updated_at

### 6. Create Test Freestyle Job
- [ ] Go to Jenkins dashboard
- [ ] Click **New Item**
- [ ] Enter name: `test-supabase-build-recorder`
- [ ] Select **Freestyle project**
- [ ] Click **OK**

### 7. Configure Build Recorder Post-Build Action
- [ ] In job configuration, scroll to **Post-build Actions**
- [ ] Click **Add post-build action**
- [ ] **Expected Result**: "Record Build Data to Supabase" option should be available
  - If NOT available, Build Recorder is not configured properly
- [ ] Select **Record Build Data to Supabase**
- [ ] Select Supabase Instance: `local-dev`
- [ ] Check options:
  - ✓ Record Artifacts
  - ✓ Record Stages
  - ✓ Record Test Results
  - ✓ Record Environment Variables (optional)
- [ ] Click **Save**

### 8. Add Simple Build Step
- [ ] Edit the job configuration
- [ ] Under **Build**, click **Add build step** > **Execute shell**
- [ ] Enter command:
  ```bash
  echo "Testing Supabase Build Recorder"
  echo "Build Number: $BUILD_NUMBER"
  echo "Job Name: $JOB_NAME"
  sleep 2
  ```
- [ ] Click **Save**

### 9. Run First Build
- [ ] Click **Build Now**
- [ ] Wait for build to complete
- [ ] Click on build #1
- [ ] Go to **Console Output**
- [ ] Verify log messages:
  - `[Supabase Build Recorder] Starting build data recording...`
  - Table creation messages
  - `[Supabase Build Recorder] Build data recorded successfully`

### 10. Verify Job Metadata in Supabase
- [ ] Open Supabase Studio: http://127.0.0.1:54323
- [ ] Go to **Table Editor** > `jenkins_jobs` table
- [ ] Verify row exists with:
  - job_name: `test-supabase-build-recorder`
  - job_full_name: `test-supabase-build-recorder`
  - table_name: `builds_test_supabase_build_recorder`
  - job_type: should be populated
  - job_url: should contain Jenkins URL

### 11. Verify Build Table Creation
- [ ] In Supabase Studio **Table Editor**
- [ ] Look for table: `builds_test_supabase_build_recorder`
- [ ] Verify table exists
- [ ] Check table structure has columns:
  - build_number (PRIMARY KEY)
  - build_id
  - build_url
  - result
  - duration_ms
  - start_time
  - end_time
  - queue_time_ms
  - node_name
  - workspace_path
  - executor_info (JSONB)
  - causes (JSONB)
  - artifacts (JSONB)
  - test_results (JSONB)
  - stages (JSONB)
  - environment_variables (JSONB)
  - custom_data (JSONB)
  - created_at

### 12. Verify Build Data
- [ ] In Supabase Studio, open `builds_test_supabase_build_recorder` table
- [ ] Verify row exists for build #1
- [ ] Check fields are populated:
  - build_number: 1
  - result: SUCCESS
  - duration_ms: should be > 0
  - start_time: should have timestamp
  - causes: should contain build cause info (manual trigger)
  - environment_variables: should be populated (if enabled)

### 13. Run Additional Builds
- [ ] Trigger build #2
- [ ] Trigger build #3
- [ ] Verify all builds are recorded in Supabase
- [ ] Check no duplicate table creation happens

### 14. Test with Folder (Optional Advanced Test)
- [ ] Create a new folder in Jenkins
- [ ] Create a job inside the folder
- [ ] Add Build Recorder post-build action
- [ ] Run build
- [ ] Verify table name includes folder path sanitization

### 15. Verify Zero Manual Configuration
- [ ] Confirm you did NOT need to:
  - Create tables manually in Supabase
  - Create SQL functions in Supabase
  - Configure PostgREST manually
  - Run any SQL scripts
- [ ] Everything was automated by the plugin ✓

## Expected Results Summary

✅ **All tests pass if:**
1. Build Recorder Configuration setup succeeds
2. jenkins_jobs table is created automatically
3. Build Recorder post-build action appears in job config
4. Job-specific build table is created on job save
5. Build data is recorded successfully
6. Multiple builds are recorded without errors
7. All data is visible in Supabase Studio
8. No manual Supabase configuration was required

## Troubleshooting

### Build Recorder not available in job config
- Check: Is Build Recorder configured in System settings?
- Check: Did "Setup and Verify" succeed?
- Check: Is buildRecorderConfigured flag set to true?

### Table creation fails
- Check: Database URL is correct
- Check: PostgreSQL JDBC driver is bundled in plugin
- Check: Supabase is running and accessible
- Check: API key has correct permissions

### Build data not recorded
- Check: Console output for error messages
- Check: Supabase instance is selected in job config
- Check: Tables exist in Supabase
- Check: Network connectivity to Supabase

### REST API verification fails
- Wait 3-5 seconds and retry (schema cache refresh)
- Check PostgREST is running (part of Supabase)
- Check table exists via SQL query
EOF

echo -e "${GREEN}✓ Testing checklist created${NC}\n"

echo -e "${GREEN}=== Environment Ready for Testing ===${NC}\n"
echo -e "Next steps:"
echo -e "1. Visit Jenkins: ${YELLOW}http://localhost:8080${NC}"
echo -e "2. Login with password: ${YELLOW}45009658f48849db9605bc386404fc41${NC}"
echo -e "3. Follow the checklist: ${YELLOW}.working_dir/TESTING_CHECKLIST.md${NC}\n"
echo -e "Supabase Studio: ${YELLOW}http://127.0.0.1:54323${NC}"
echo -e ""

