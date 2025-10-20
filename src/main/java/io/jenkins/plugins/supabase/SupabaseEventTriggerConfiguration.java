package io.jenkins.plugins.supabase;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class SupabaseEventTriggerConfiguration extends GlobalConfiguration {

    private static final Logger LOGGER = Logger.getLogger(SupabaseEventTriggerConfiguration.class.getName());

    private List<SupabaseInstance> supabaseInstances;
    
    // Build Recorder Configuration fields
    private String buildRecorderInstanceName;
    private String buildRecorderSchema = "public";
    private String buildRecorderJobTable = "jenkins_jobs";
    private boolean buildRecorderConfigured = false;

    public SupabaseEventTriggerConfiguration() {
        load();
    }

    public static SupabaseEventTriggerConfiguration get() {
        return GlobalConfiguration.all().get(SupabaseEventTriggerConfiguration.class);
    }

    public List<SupabaseInstance> getSupabaseInstances() {
        return supabaseInstances;
    }

    @DataBoundSetter
    public void setSupabaseInstances(List<SupabaseInstance> supabaseInstances) {
        this.supabaseInstances = supabaseInstances;
        save();
    }

    // Build Recorder Configuration getters and setters
    public String getBuildRecorderInstanceName() {
        return buildRecorderInstanceName;
    }

    @DataBoundSetter
    public void setBuildRecorderInstanceName(String buildRecorderInstanceName) {
        this.buildRecorderInstanceName = buildRecorderInstanceName;
        save();
    }

    public String getBuildRecorderSchema() {
        return buildRecorderSchema;
    }

    @DataBoundSetter
    public void setBuildRecorderSchema(String buildRecorderSchema) {
        this.buildRecorderSchema = buildRecorderSchema;
        save();
    }

    public String getBuildRecorderJobTable() {
        return buildRecorderJobTable;
    }

    @DataBoundSetter
    public void setBuildRecorderJobTable(String buildRecorderJobTable) {
        this.buildRecorderJobTable = buildRecorderJobTable;
        save();
    }

    public boolean isBuildRecorderConfigured() {
        return buildRecorderConfigured;
    }

    @DataBoundSetter
    public void setBuildRecorderConfigured(boolean buildRecorderConfigured) {
        this.buildRecorderConfigured = buildRecorderConfigured;
        save();
    }

    @Override
    @SuppressWarnings("deprecation") // StaplerRequest.bindJSON is deprecated but still required for Jenkins compatibility
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        save();
        return true;
    }

    public FormValidation doCheckUrl(@QueryParameter String value) {
        if (value == null || value.trim().isEmpty()) {
            return FormValidation.error("URL is required");
        }
        if (!value.startsWith("http://") && !value.startsWith("https://") && !value.startsWith("ws://") && !value.startsWith("wss://")) {
            return FormValidation.error("URL must start with http://, https://, ws://, or wss://");
        }
        return FormValidation.ok();
    }

    public FormValidation doCheckName(@QueryParameter String value) {
        if (value == null || value.trim().isEmpty()) {
            return FormValidation.error("Name is required");
        }
        return FormValidation.ok();
    }

    public SupabaseInstance getInstanceByName(String name) {
        if (supabaseInstances == null || name == null) {
            return null;
        }
        for (SupabaseInstance instance : supabaseInstances) {
            if (name.equals(instance.getName())) {
                return instance;
            }
        }
        return null;
    }

    public List<String> getInstanceNames() {
        if (supabaseInstances == null) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (SupabaseInstance instance : supabaseInstances) {
            names.add(instance.getName());
        }
        return names;
    }

    public ListBoxModel doFillCredentialsIdItems(@QueryParameter String credentialsId) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null || !jenkins.hasPermission(Jenkins.ADMINISTER)) {
            return new StandardListBoxModel().includeCurrentValue(credentialsId);
        }
        
        return new StandardListBoxModel()
            .includeEmptyValue()
            .includeAs(ACL.SYSTEM, jenkins, StringCredentials.class);
    }

    /**
     * Populate the Build Recorder instance dropdown.
     */
    public ListBoxModel doFillBuildRecorderInstanceNameItems() {
        ListBoxModel items = new ListBoxModel();
        items.add("-- Select Supabase Instance --", "");
        
        if (supabaseInstances != null) {
            for (SupabaseInstance instance : supabaseInstances) {
                items.add(instance.getName(), instance.getName());
            }
        }
        return items;
    }

    /**
     * Handler for "Setup and Verify" button in Build Recorder Configuration.
     * This method creates the job metadata table and verifies the setup.
     */
    public HttpResponse doSetupAndVerify(
            @QueryParameter String buildRecorderInstanceName,
            @QueryParameter String buildRecorderSchema,
            @QueryParameter String buildRecorderJobTable) {
        
        try {
            return performSetupAndVerify(buildRecorderInstanceName, buildRecorderSchema, buildRecorderJobTable);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in doSetupAndVerify", e);
            
            // Get full stack trace
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String stackTrace = sw.toString();
            
            return jsonResponse("error", "Unexpected error: " + e.getMessage() + "\n" + stackTrace);
        }
    }

    /**
     * Performs the actual setup and verification logic.
     * Separated from doSetupAndVerify to ensure exception handling.
     */
    private HttpResponse performSetupAndVerify(
            String instanceName,
            String schema,
            String jobTable) throws IOException, InterruptedException {
        
        // Validate inputs
        if (instanceName == null || instanceName.trim().isEmpty()) {
            return jsonResponse("error", "Please select a Supabase instance");
        }
        
        if (schema == null || schema.trim().isEmpty()) {
            return jsonResponse("error", "Schema name is required");
        }
        
        if (jobTable == null || jobTable.trim().isEmpty()) {
            return jsonResponse("error", "Job metadata table name is required");
        }

        // Get the instance
        SupabaseInstance instance = getInstanceByName(instanceName);
        if (instance == null) {
            return jsonResponse("error", "Supabase instance '" + instanceName + "' not found");
        }

        // Check if DB URL is configured
        if (instance.getDbUrl() == null || instance.getDbUrl().trim().isEmpty()) {
            return jsonResponse("error", "Database URL is not configured for instance '" + instanceName + "'. Please configure it in the Supabase Instance settings.");
        }

        // Create a simple TaskListener for logging
        TaskListener listener = new TaskListener() {
            @Override
            public PrintStream getLogger() {
                return System.out;
            }
        };

        // Create the data client
        SupabaseDataClient dataClient = new SupabaseDataClient(instance, listener);

        try {
            // Step 1: Create job metadata table
            dataClient.createJobMetadataTable(schema, jobTable);

            // Step 2: Wait for PostgREST schema cache to refresh
            dataClient.waitForSchemaCache();

            // Step 3: Verify table exists
            if (!dataClient.verifyTableExists(jobTable)) {
                return jsonResponse("error", "Failed to verify table creation. Table '" + jobTable + "' is not accessible via REST API.");
            }

            // Step 4: Save configuration
            this.buildRecorderInstanceName = instanceName;
            this.buildRecorderSchema = schema;
            this.buildRecorderJobTable = jobTable;
            this.buildRecorderConfigured = true;
            save();

            return jsonResponse("ok", "✓ Build Recorder configured successfully! Job metadata table '" + schema + "." + jobTable + "' is ready.");

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to set up Build Recorder", e);
            return jsonResponse("error", "Failed to create job metadata table: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.SEVERE, "Setup interrupted", e);
            return jsonResponse("error", "Setup was interrupted: " + e.getMessage());
        }
    }

    /**
     * Helper method to create JSON responses for validateButton.
     * Returns properly formatted JSON for Jenkins UI.
     */
    private HttpResponse jsonResponse(String kind, String message) {
        // Escape message for JSON
        String escapedMessage = message
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
        
        String json = "{\"kind\":\"" + kind + "\",\"message\":\"" + escapedMessage + "\"}";
        
        return HttpResponses.html(json);
    }
}
