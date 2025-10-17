# Comprehensive Functional Test Plan for Jenkins Supabase Plugin
## Test Date: October 17, 2025
## Plugin Version: 1.1.0-SNAPSHOT

---

## Test Suite 1: Event Trigger Tests

### Test 1.1: INSERT Event
- **Action**: Insert a new record into test_table
- **Expected**: Build triggered, record data captured in build cause
- **Verify**: Environment variables contain event data

### Test 1.2: UPDATE Event
- **Action**: Update an existing record in test_table
- **Expected**: Build triggered, both old and new record data in cause
- **Verify**: Environment variables show before/after values

### Test 1.3: DELETE Event
- **Action**: Delete a record from test_table
- **Expected**: Build triggered, deleted record data in cause
- **Verify**: Environment variables contain deleted record

### Test 1.4: Multiple Rapid Events
- **Action**: Insert 3 records in quick succession
- **Expected**: 3 separate builds triggered
- **Verify**: All builds complete successfully, no events lost

---

## Test Suite 2: Build Recorder Tests

### Test 2.1: Basic Build Recording
- **Action**: Trigger build via INSERT event
- **Expected**: Build data recorded in builds_supabase_event_demo table
- **Verify**: 
  - build_number, result, duration_ms populated
  - start_time, end_time recorded
  - causes JSON contains event information

### Test 2.2: Artifact Recording
- **Action**: Build with artifacts
- **Expected**: Artifacts captured in artifacts JSONB field
- **Verify**: artifact filenames, paths, URLs correct

### Test 2.3: Job Metadata Upsert
- **Action**: Multiple builds from same job
- **Expected**: Only one entry in jobs table
- **Verify**: updated_at timestamp changes, job metadata accurate

### Test 2.4: Environment Variables Recording
- **Action**: Build with environment variables enabled
- **Expected**: env vars recorded (sensitive ones filtered)
- **Verify**: No PASSWORD, SECRET, or TOKEN vars present

### Test 2.5: Custom Fields
- **Action**: Configure custom JSON fields
- **Expected**: custom_data JSONB populated
- **Verify**: Custom fields parsed and stored correctly

---

## Test Suite 3: REST API Integration Tests

### Test 3.1: Insert Operation
- **Action**: New build triggers insertRecord()
- **Expected**: HTTP 201 response, data in database
- **Verify**: Query database directly to confirm

### Test 3.2: Upsert Operation
- **Action**: Second build from same job
- **Expected**: Jobs table updated, not duplicated
- **Verify**: Only one row in jobs table for the job

### Test 3.3: Error Handling
- **Action**: Invalid data or table missing
- **Expected**: Graceful error, build marked failed
- **Verify**: Error logged, no plugin crash

---

## Test Suite 4: Concurrent Build Tests

### Test 4.1: Simultaneous Builds
- **Action**: Trigger 3 builds at same time
- **Expected**: All 3 complete successfully
- **Verify**: All 3 records in database, no race conditions

### Test 4.2: Job Metadata Race Condition
- **Action**: First-time job with concurrent builds
- **Expected**: Jobs table has single entry
- **Verify**: Upsert prevents duplicates

---

## Test Suite 5: End-to-End Integration Tests

### Test 5.1: Full Workflow - INSERT
1. Insert record → Build triggered → Data recorded
2. Verify: Complete chain works end-to-end

### Test 5.2: Full Workflow - UPDATE
1. Update record → Build triggered → Data recorded
2. Verify: Old and new values captured

### Test 5.3: Full Workflow - DELETE
1. Delete record → Build triggered → Data recorded
2. Verify: Deleted record data preserved

---

## Test Results Template

| Test ID | Status | Notes |
|---------|--------|-------|
| 1.1     |        |       |
| 1.2     |        |       |
| ...     |        |       |

---

## Success Criteria
- All event types trigger builds correctly
- All build data recorded accurately
- REST API calls succeed
- No race conditions in concurrent scenarios
- Error handling works gracefully
- Data integrity maintained across all operations
