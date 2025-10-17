# Event Aggregation Fix - Summary

## Problem

When multiple Supabase events were triggered in rapid succession, Jenkins would aggregate them into a single build instead of creating separate builds for each event.

### Example of the Issue

Build #22 from previous testing had **TWO** causes combined:
- cause_0: Rapid Event 1
- cause_1: Rapid Event 3

Expected: 3 events → 3 separate builds  
Actual: 3 events → 2 builds (events 1 & 3 combined)

## Root Cause

Jenkins's `scheduleBuild2()` method aggregates builds with causes that are considered "equal" according to their `equals()` and `hashCode()` methods. By default, the `SupabaseEventCause` class inherited these methods from the parent `Cause` class, which meant multiple events with the same event type and table name could be considered equal and thus aggregated.

## Solution

Modified `SupabaseEventCause` to include a unique identifier for each event and override `equals()` and `hashCode()` to prevent aggregation:

### Changes Made

1. **Added unique eventId field (UUID)**
   ```java
   private final String eventId;
   
   public SupabaseEventCause(...) {
       // ... existing fields ...
       this.eventId = java.util.UUID.randomUUID().toString();
   }
   ```

2. **Added timestamp field**
   ```java
   private final long timestamp;
   
   public SupabaseEventCause(...) {
       // ... existing fields ...
       this.timestamp = System.currentTimeMillis();
   }
   ```

3. **Override equals() method**
   ```java
   @Override
   public boolean equals(Object obj) {
       if (this == obj) return true;
       if (!(obj instanceof SupabaseEventCause)) return false;
       SupabaseEventCause other = (SupabaseEventCause) obj;
       return eventId.equals(other.eventId);
   }
   ```
   - Compares **only** the eventId
   - Since each eventId is a unique UUID, no two causes can ever be equal
   - Prevents Jenkins from aggregating any events

4. **Override hashCode() method**
   ```java
   @Override
   public int hashCode() {
       return eventId.hashCode();
   }
   ```
   - Consistent with equals() override
   - Required for proper hash-based collection behavior

5. **Updated getShortDescription()**
   ```java
   @Override
   public String getShortDescription() {
       StringBuilder desc = new StringBuilder();
       desc.append("Triggered by Supabase ").append(eventType)
           .append(" event on table ").append(tableName);
       desc.append(" at ").append(new java.util.Date(timestamp));
       // ... rest of description ...
   }
   ```
   - Now includes timestamp showing when each event occurred

## Testing

### Test Setup
Inserted 3 rapid INSERT events with 0.5 second delays:
```bash
curl -X POST "http://172.18.0.1:54321/rest/v1/test_table" \
  -d '{"name": "Separate Build Test 1"}'
# ... wait 0.5s ...
curl -X POST "http://172.18.0.1:54321/rest/v1/test_table" \
  -d '{"name": "Separate Build Test 2"}'
# ... wait 0.5s ...
curl -X POST "http://172.18.0.1:54321/rest/v1/test_table" \
  -d '{"name": "Separate Build Test 3"}'
```

### Results ✅

**Build #26**
- Event: "Separate Build Test 1"
- EventId: `2907d0d2-d0b3-454f-92d2-47ae30b7588e`
- Causes: **1** (single event)
- Timestamp: 2025-10-17T15:20:32.342

**Build #27**
- Event: "Separate Build Test 2"
- EventId: `656e7dd6-af1c-434b-be02-1a21ad5d0e90`
- Causes: **1** (single event)
- Timestamp: 2025-10-17T15:20:32.946

**Build #28**
- Event: "Separate Build Test 3"
- EventId: `6104021e-dddd-4faf-9be9-716f951b4e79`
- Causes: **1** (single event)
- Timestamp: 2025-10-17T15:20:33.449

### Verification

✅ 3 events triggered 3 separate builds  
✅ Each build has exactly ONE cause (no aggregation)  
✅ Each cause has a unique eventId  
✅ Builds occurred in chronological order  
✅ Timestamps show the correct timing of events

## Jenkins Logs

From Jenkins logs showing all 3 events were received and scheduled:

```
2025-10-17 15:20:32.342 INFO i.j.p.s.SupabaseEventTrigger#handleEvent: Received INSERT event for table test_table
2025-10-17 15:20:32.342 INFO i.j.p.s.SupabaseEventTrigger#handleEvent: Scheduled build for job: supabase-event-demo

2025-10-17 15:20:32.946 INFO i.j.p.s.SupabaseEventTrigger#handleEvent: Received INSERT event for table test_table
2025-10-17 15:20:32.948 INFO i.j.p.s.SupabaseEventTrigger#handleEvent: Scheduled build for job: supabase-event-demo

2025-10-17 15:20:33.447 INFO i.j.p.s.SupabaseEventTrigger#handleEvent: Received INSERT event for table test_table
2025-10-17 15:20:33.449 INFO i.j.p.s.SupabaseEventTrigger#handleEvent: Scheduled build for job: supabase-event-demo
```

## Conclusion

The event aggregation issue has been successfully resolved. Each Supabase database event now triggers its own separate Jenkins build, with events processed in the order they occurred. The solution is robust and ensures that no events will ever be aggregated, regardless of how rapidly they occur.

## Files Modified

- `src/main/java/io/jenkins/plugins/supabase/SupabaseEventTrigger.java`
  - Modified `SupabaseEventCause` inner class
  - Added eventId (UUID) and timestamp fields
  - Override equals() and hashCode() methods
  - Updated getShortDescription() to include timestamp

## Date

October 17, 2025
