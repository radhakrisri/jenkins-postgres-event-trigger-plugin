package io.jenkins.plugins.supabase;

import hudson.EnvVars;
import hudson.model.EnvironmentContributingAction;
import hudson.model.InvisibleAction;
import hudson.model.Run;

import javax.annotation.Nonnull;

/**
 * Action that contributes Supabase event data as environment variables to a build.
 */
public class SupabaseEventEnvironmentContributor extends InvisibleAction implements EnvironmentContributingAction {
    
    private final String eventType;
    private final String tableName;
    private final String schema;
    private final String eventData;
    private final String recordOld;
    private final String recordNew;

    public SupabaseEventEnvironmentContributor(String eventType, String tableName, String schema, 
                                               String eventData, String recordOld, String recordNew) {
        this.eventType = eventType;
        this.tableName = tableName;
        this.schema = schema;
        this.eventData = eventData;
        this.recordOld = recordOld;
        this.recordNew = recordNew;
    }

    @Override
    public void buildEnvironment(Run<?, ?> run, EnvVars envVars) {
        if (eventType != null) {
            envVars.put("SUPABASE_EVENT_TYPE", eventType);
            envVars.put("SUPABASE_EVENT_OPERATION", eventType); // Alias for consistency
        }
        if (tableName != null) {
            envVars.put("SUPABASE_EVENT_TABLE", tableName);
        }
        if (schema != null) {
            envVars.put("SUPABASE_EVENT_SCHEMA", schema);
        }
        if (eventData != null) {
            envVars.put("SUPABASE_EVENT_DATA", eventData);
        }
        if (recordOld != null) {
            envVars.put("SUPABASE_EVENT_RECORD_OLD", recordOld);
        }
        if (recordNew != null) {
            envVars.put("SUPABASE_EVENT_RECORD_NEW", recordNew);
        }
    }

    public String getEventType() {
        return eventType;
    }

    public String getTableName() {
        return tableName;
    }

    public String getSchema() {
        return schema;
    }

    public String getEventData() {
        return eventData;
    }

    public String getRecordOld() {
        return recordOld;
    }

    public String getRecordNew() {
        return recordNew;
    }
}
