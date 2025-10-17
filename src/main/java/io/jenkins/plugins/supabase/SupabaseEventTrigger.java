package io.jenkins.plugins.supabase;

import com.google.gson.JsonObject;
import hudson.Extension;
import hudson.model.*;
import hudson.scm.ChangeLogSet;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.ParameterizedJobMixIn;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SupabaseEventTrigger extends Trigger<Job<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(SupabaseEventTrigger.class.getName());
    
    private final String instanceName;
    private final String tables;
    private boolean subscribeInsert = false;
    private boolean subscribeUpdate = false;
    private boolean subscribeDelete = false;
    
    // Legacy field for backward compatibility (no longer used)
    @Deprecated
    private transient SupabaseRealtimeClient client;
    private transient Set<String> subscribedTables = new HashSet<>();
    
    // Unique subscriber ID for this trigger instance
    private transient String subscriberId;

    @DataBoundConstructor
    public SupabaseEventTrigger(String instanceName, String tables) {
        this.instanceName = instanceName;
        this.tables = tables;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public String getTables() {
        return tables;
    }

    public boolean isSubscribeInsert() {
        return subscribeInsert;
    }

    @DataBoundSetter
    public void setSubscribeInsert(boolean subscribeInsert) {
        this.subscribeInsert = subscribeInsert;
    }

    public boolean isSubscribeUpdate() {
        return subscribeUpdate;
    }

    @DataBoundSetter
    public void setSubscribeUpdate(boolean subscribeUpdate) {
        this.subscribeUpdate = subscribeUpdate;
    }

    public boolean isSubscribeDelete() {
        return subscribeDelete;
    }

    @DataBoundSetter
    public void setSubscribeDelete(boolean subscribeDelete) {
        this.subscribeDelete = subscribeDelete;
    }

    @Override
    public void start(Job<?, ?> job, boolean newInstance) {
        super.start(job, newInstance);
        LOGGER.info("Starting SupabaseEventTrigger for job: " + job.getName());
        
        // Initialize transient fields
        if (subscribedTables == null) {
            subscribedTables = new HashSet<>();
        }
        
        // Generate unique subscriber ID for this trigger
        subscriberId = job.getFullName() + "-" + System.currentTimeMillis();
        
        try {
            SupabaseEventTriggerConfiguration config = SupabaseEventTriggerConfiguration.get();
            SupabaseInstance instance = config.getInstanceByName(instanceName);
            
            if (instance == null) {
                LOGGER.warning("Supabase instance not found: " + instanceName);
                return;
            }
            
            // Use connection manager for pooled connections
            subscribeToTables(job, instance);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error starting SupabaseEventTrigger", e);
        }
    }

    private void subscribeToTables(Job<?, ?> job, SupabaseInstance instance) {
        if (tables == null || tables.trim().isEmpty()) {
            return;
        }
        
        SupabaseConnectionManager manager = SupabaseConnectionManager.getInstance();
        
        String[] tableArray = tables.split("[,;\\s]+");
        for (String table : tableArray) {
            if (table.trim().isEmpty()) {
                continue;
            }
            
            String schema = "public";
            String tableName = table.trim();
            
            if (tableName.contains(".")) {
                String[] parts = tableName.split("\\.", 2);
                schema = parts[0];
                tableName = parts[1];
            }
            
            // Collect events to subscribe to
            java.util.List<String> events = new java.util.ArrayList<>();
            java.util.Map<String, java.util.function.Consumer<JsonObject>> handlers = new java.util.HashMap<>();
            
            final String finalSchema = schema;
            final String finalTableName = tableName;
            
            if (subscribeInsert) {
                events.add("INSERT");
                handlers.put("INSERT", payload -> handleEvent(job, "INSERT", finalTableName, payload));
            }
            if (subscribeUpdate) {
                events.add("UPDATE");
                handlers.put("UPDATE", payload -> handleEvent(job, "UPDATE", finalTableName, payload));
            }
            if (subscribeDelete) {
                events.add("DELETE");
                handlers.put("DELETE", payload -> handleEvent(job, "DELETE", finalTableName, payload));
            }
            
            // Subscribe using connection manager (enables connection pooling)
            if (!events.isEmpty()) {
                manager.subscribe(
                    instanceName,
                    instance.getUrl(),
                    instance.getApiKey(),
                    finalSchema,
                    finalTableName,
                    events,
                    handlers,
                    subscriberId
                );
                
                subscribedTables.add(schema + "." + tableName);
                LOGGER.info(String.format("Subscribed job %s to %s.%s for events %s", 
                                         job.getName(), schema, tableName, events));
            }
        }
    }

    private void handleEvent(Job<?, ?> job, String eventType, String tableName, JsonObject payload) {
        LOGGER.info("Received " + eventType + " event for table " + tableName);
        
        try {
            // Extract event data from payload
            String schema = payload.has("schema") ? payload.get("schema").getAsString() : "public";
            String eventData = payload.toString();
            
            // Extract record data
            String recordOld = null;
            String recordNew = null;
            
            if (payload.has("old_record") && !payload.get("old_record").isJsonNull()) {
                recordOld = payload.get("old_record").toString();
            }
            if (payload.has("record") && !payload.get("record").isJsonNull()) {
                recordNew = payload.get("record").toString();
            }
            
            long timestamp = System.currentTimeMillis();
            
            // Create environment contributor action
            SupabaseEventEnvironmentContributor envAction = new SupabaseEventEnvironmentContributor(
                eventType, tableName, schema, eventData, recordOld, recordNew
            );
            
            // Create cause with simplified description (just table, type, and when)
            CauseAction causeAction = new CauseAction(new SupabaseEventCause(eventType, tableName, timestamp));
            
            // Create action to hold change data for the Changes section
            SupabaseEventChangeAction changeAction = new SupabaseEventChangeAction(
                eventType, tableName, recordNew, recordOld, timestamp
            );
            
            if (job instanceof ParameterizedJobMixIn.ParameterizedJob) {
                ParameterizedJobMixIn<?, ?> pJob = new ParameterizedJobMixIn() {
                    @Override
                    protected Job asJob() {
                        return job;
                    }
                };
                pJob.scheduleBuild2(0, causeAction, envAction, changeAction);
                LOGGER.info("Scheduled build for job: " + job.getName());
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error triggering build", e);
        }
    }

    @Override
    public void stop() {
        LOGGER.info("Stopping SupabaseEventTrigger for subscriber: " + subscriberId);
        
        SupabaseConnectionManager manager = SupabaseConnectionManager.getInstance();
        
        // Unsubscribe from all tables
        for (String table : subscribedTables) {
            String[] parts = table.split("\\.", 2);
            if (parts.length == 2) {
                manager.unsubscribe(instanceName, parts[0], parts[1], subscriberId);
            }
        }
        
        subscribedTables.clear();
        super.stop();
    }

    /**
     * Action that carries Supabase event change data to be displayed in the Changes section (invisible).
     */
    public static class SupabaseEventChangeAction extends InvisibleAction {
        private final String eventType;
        private final String tableName;
        private final String recordNew;
        private final String recordOld;
        private final long timestamp;

        public SupabaseEventChangeAction(String eventType, String tableName, String recordNew, String recordOld, long timestamp) {
            this.eventType = eventType;
            this.tableName = tableName;
            this.recordNew = recordNew;
            this.recordOld = recordOld;
            this.timestamp = timestamp;
        }

        public String getEventType() {
            return eventType;
        }

        public String getTableName() {
            return tableName;
        }

        public String getRecordNew() {
            return recordNew;
        }

        public String getRecordOld() {
            return recordOld;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Display action for Supabase database changes - appears in the Changes section of build page.
     */
    public static class SupabaseChangesDisplayAction implements Action {
        private final List<SupabaseEventChangeAction> changes;

        public SupabaseChangesDisplayAction(List<SupabaseEventChangeAction> changes) {
            this.changes = changes;
        }

        @Override
        public String getIconFileName() {
            return "symbol-changes";
        }

        @Override
        public String getDisplayName() {
            return "Supabase Changes";
        }

        @Override
        public String getUrlName() {
            return "supabase-changes";
        }

        public List<SupabaseEventChangeAction> getChanges() {
            return changes;
        }
        
        public String formatTimestamp(long timestamp) {
            return new java.util.Date(timestamp).toString();
        }
        
        public String formatJson(String json) {
            if (json == null) return "";
            // Add basic pretty printing
            return json.replace(",", ",\n  ");
        }
    }

    public static class SupabaseEventCause extends Cause {
        private final String eventType;
        private final String tableName;
        private final long timestamp;
        private final String eventId;

        public SupabaseEventCause(String eventType, String tableName, long timestamp) {
            this.eventType = eventType;
            this.tableName = tableName;
            this.timestamp = timestamp;
            this.eventId = java.util.UUID.randomUUID().toString();
        }
        
        /**
         * Prevent Jenkins from aggregating multiple events into a single build.
         * Each event should trigger its own build in the order received.
         */
        @Override
        public boolean equals(Object obj) {
            // Make each cause unique by comparing eventId
            if (this == obj) return true;
            if (!(obj instanceof SupabaseEventCause)) return false;
            SupabaseEventCause other = (SupabaseEventCause) obj;
            return eventId.equals(other.eventId);
        }
        
        @Override
        public int hashCode() {
            return eventId.hashCode();
        }

        @Override
        public String getShortDescription() {
            return String.format("Supabase %s event on table %s at %s", 
                eventType, tableName, new java.util.Date(timestamp));
        }
        
        public String getEventType() {
            return eventType;
        }
        
        public String getTableName() {
            return tableName;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
    
    /**
     * Builds a WebSocket URL from a Supabase API URL.
     * Converts http(s):// to ws(s):// and appends the Realtime WebSocket path with required query parameters.
     * 
     * @deprecated This method is no longer used. SupabaseConnectionManager handles URL construction.
     * @param apiUrl The Supabase API URL (e.g., http://localhost:54321 or https://your-project.supabase.co)
     * @param apiKey The Supabase API key for authentication
     * @return The WebSocket URL with proper protocol, path, and query parameters
     */
    @Deprecated
    @SuppressWarnings("unused")
    private String buildWebSocketUrl(String apiUrl, String apiKey) {
        // Remove trailing slash if present
        if (apiUrl.endsWith("/")) {
            apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
        }
        
        // Convert HTTP(S) to WS(S)
        String wsUrl = apiUrl.replace("https://", "wss://").replace("http://", "ws://");
        
        // Append Realtime WebSocket endpoint with required query parameters
        wsUrl = wsUrl + "/realtime/v1/websocket?apikey=" + apiKey + "&vsn=1.0.0";
        
        return wsUrl;
    }

    /**
     * Represents a Supabase database event change in the Jenkins Changes section.
     */
    public static class SupabaseChangeLogEntry extends ChangeLogSet.Entry {
        private final String eventType;
        private final String tableName;
        private final String recordNew;
        private final String recordOld;
        private final long timestamp;

        public SupabaseChangeLogEntry(String eventType, String tableName, String recordNew, String recordOld, long timestamp, SupabaseChangeLogSet parent) {
            this.eventType = eventType;
            this.tableName = tableName;
            this.recordNew = recordNew;
            this.recordOld = recordOld;
            this.timestamp = timestamp;
            setParent(parent);
        }

        @Override
        public String getMsg() {
            StringBuilder msg = new StringBuilder();
            msg.append(eventType).append(" event on table ").append(tableName);
            msg.append(" at ").append(new java.util.Date(timestamp));
            return msg.toString();
        }

        @Override
        public User getAuthor() {
            return User.getUnknown();
        }

        @Override
        public Collection<String> getAffectedPaths() {
            return Collections.singletonList(tableName);
        }

        @Override
        public String getMsgAnnotated() {
            StringBuilder msg = new StringBuilder();
            msg.append("<b>").append(eventType).append("</b> event on table <code>").append(tableName).append("</code>");
            
            if ("INSERT".equals(eventType) && recordNew != null) {
                msg.append("<br/><b>New record:</b><br/><pre>").append(formatJson(recordNew)).append("</pre>");
            } else if ("UPDATE".equals(eventType)) {
                if (recordOld != null) {
                    msg.append("<br/><b>Old record:</b><br/><pre>").append(formatJson(recordOld)).append("</pre>");
                }
                if (recordNew != null) {
                    msg.append("<br/><b>New record:</b><br/><pre>").append(formatJson(recordNew)).append("</pre>");
                }
            } else if ("DELETE".equals(eventType) && recordOld != null) {
                msg.append("<br/><b>Deleted record:</b><br/><pre>").append(formatJson(recordOld)).append("</pre>");
            }
            
            return msg.toString();
        }
        
        private String formatJson(String json) {
            // Basic JSON formatting with proper escaping
            return json.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        public String getEventType() {
            return eventType;
        }

        public String getTableName() {
            return tableName;
        }

        public String getRecordNew() {
            return recordNew;
        }

        public String getRecordOld() {
            return recordOld;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Container for Supabase database event changes.
     */
    public static class SupabaseChangeLogSet extends ChangeLogSet<SupabaseChangeLogEntry> {
        private final List<SupabaseChangeLogEntry> entries;

        protected SupabaseChangeLogSet(Run<?, ?> run, List<SupabaseChangeLogEntry> entries) {
            super(run, null);
            this.entries = entries;
        }

        @Override
        public boolean isEmptySet() {
            return entries.isEmpty();
        }

        @Override
        public Iterator<SupabaseChangeLogEntry> iterator() {
            return entries.iterator();
        }

        @Override
        public String getKind() {
            return "supabase";
        }
    }

    @Extension
    public static final class DescriptorImpl extends TriggerDescriptor {

        @Override
        public boolean isApplicable(Item item) {
            return item instanceof Job && item instanceof ParameterizedJobMixIn.ParameterizedJob;
        }

        @Override
        public String getDisplayName() {
            return "Supabase Event Trigger";
        }

        public ListBoxModel doFillInstanceNameItems() {
            ListBoxModel items = new ListBoxModel();
            SupabaseEventTriggerConfiguration config = SupabaseEventTriggerConfiguration.get();
            
            if (config != null) {
                List<String> instanceNames = config.getInstanceNames();
                for (String name : instanceNames) {
                    items.add(name, name);
                }
            }
            
            if (items.isEmpty()) {
                items.add("No instances configured", "");
            }
            
            return items;
        }

        public FormValidation doCheckTables(@QueryParameter String value) {
            if (value == null || value.trim().isEmpty()) {
                return FormValidation.error("At least one table must be specified");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckInstanceName(@QueryParameter String value) {
            if (value == null || value.trim().isEmpty()) {
                return FormValidation.error("Instance name is required");
            }
            return FormValidation.ok();
        }
    }
}
