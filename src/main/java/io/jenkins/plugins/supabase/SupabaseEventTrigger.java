package io.jenkins.plugins.supabase;

import com.google.gson.JsonObject;
import hudson.Extension;
import hudson.model.*;
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
    
    private transient SupabaseRealtimeClient client;
    private transient Set<String> subscribedTables = new HashSet<>();

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
        
        try {
            SupabaseEventTriggerConfiguration config = SupabaseEventTriggerConfiguration.get();
            SupabaseInstance instance = config.getInstanceByName(instanceName);
            
            if (instance == null) {
                LOGGER.warning("Supabase instance not found: " + instanceName);
                return;
            }
            
            // Construct WebSocket URL from Supabase API URL
            String apiUrl = instance.getUrl();
            String wsUrl = buildWebSocketUrl(apiUrl, instance.getApiKey().getPlainText());
            
            client = new SupabaseRealtimeClient(wsUrl, null);
            client.connect();
            
            // Wait for connection
            int attempts = 0;
            while (!client.isOpen() && attempts < 10) {
                Thread.sleep(500);
                attempts++;
            }
            
            if (client.isOpen()) {
                subscribeToTables(job);
            } else {
                LOGGER.warning("Failed to connect to Supabase Realtime");
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error starting SupabaseEventTrigger", e);
        }
    }

    private void subscribeToTables(Job<?, ?> job) {
        if (tables == null || tables.trim().isEmpty()) {
            return;
        }
        
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
            
            final String finalSchema = schema;
            final String finalTableName = tableName;
            
            if (subscribeInsert) {
                client.subscribeToTable(finalSchema, finalTableName, "INSERT", 
                    payload -> handleEvent(job, "INSERT", finalTableName, payload));
            }
            if (subscribeUpdate) {
                client.subscribeToTable(finalSchema, finalTableName, "UPDATE", 
                    payload -> handleEvent(job, "UPDATE", finalTableName, payload));
            }
            if (subscribeDelete) {
                client.subscribeToTable(finalSchema, finalTableName, "DELETE", 
                    payload -> handleEvent(job, "DELETE", finalTableName, payload));
            }
            
            subscribedTables.add(schema + "." + tableName);
        }
    }

    private void handleEvent(Job<?, ?> job, String eventType, String tableName, JsonObject payload) {
        LOGGER.info("Received " + eventType + " event for table " + tableName);
        
        try {
            List<ParameterValue> parameters = new ArrayList<>();
            parameters.add(new StringParameterValue("SUPABASE_EVENT_TYPE", eventType));
            parameters.add(new StringParameterValue("SUPABASE_TABLE_NAME", tableName));
            parameters.add(new StringParameterValue("SUPABASE_EVENT_DATA", payload.toString()));
            
            ParametersAction parametersAction = new ParametersAction(parameters);
            CauseAction causeAction = new CauseAction(new SupabaseEventCause(eventType, tableName));
            
            if (job instanceof ParameterizedJobMixIn.ParameterizedJob) {
                ParameterizedJobMixIn<?, ?> pJob = new ParameterizedJobMixIn() {
                    @Override
                    protected Job asJob() {
                        return job;
                    }
                };
                pJob.scheduleBuild2(0, parametersAction, causeAction);
                LOGGER.info("Scheduled build for job: " + job.getName());
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error triggering build", e);
        }
    }

    @Override
    public void stop() {
        LOGGER.info("Stopping SupabaseEventTrigger");
        if (client != null && client.isOpen()) {
            for (String table : subscribedTables) {
                String[] parts = table.split("\\.", 2);
                if (parts.length == 2) {
                    client.unsubscribeFromTable(parts[0], parts[1]);
                }
            }
            client.close();
        }
        super.stop();
    }

    public static class SupabaseEventCause extends Cause {
        private final String eventType;
        private final String tableName;

        public SupabaseEventCause(String eventType, String tableName) {
            this.eventType = eventType;
            this.tableName = tableName;
        }

        @Override
        public String getShortDescription() {
            return "Triggered by Supabase " + eventType + " event on table " + tableName;
        }
    }
    
    /**
     * Builds a WebSocket URL from a Supabase API URL.
     * Converts http(s):// to ws(s):// and appends the Realtime WebSocket path with required query parameters.
     * 
     * @param apiUrl The Supabase API URL (e.g., http://localhost:54321 or https://your-project.supabase.co)
     * @param apiKey The Supabase API key for authentication
     * @return The WebSocket URL with proper protocol, path, and query parameters
     */
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
