package io.jenkins.plugins.supabase;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.EnvVars;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Post-build action that records comprehensive build data to Supabase.
 * Creates a table based on the job path and records detailed build information.
 */
public class SupabaseBuildRecorder extends Recorder implements SimpleBuildStep {

    private static final Logger LOGGER = Logger.getLogger(SupabaseBuildRecorder.class.getName());

    private final String instanceName;
    private boolean recordArtifacts = true;
    private boolean recordStages = true;
    private boolean recordTestResults = true;
    private boolean recordEnvironmentVariables = false;
    private String customFields = "";

    @DataBoundConstructor
    public SupabaseBuildRecorder(String instanceName) {
        this.instanceName = instanceName;
        // Note: Table creation happens when job is saved via JobListener
        // See SupabaseBuildRecorderJobListener class
    }

    public String getInstanceName() {
        return instanceName;
    }

    public boolean isRecordArtifacts() {
        return recordArtifacts;
    }

    @DataBoundSetter
    public void setRecordArtifacts(boolean recordArtifacts) {
        this.recordArtifacts = recordArtifacts;
    }

    public boolean isRecordStages() {
        return recordStages;
    }

    @DataBoundSetter
    public void setRecordStages(boolean recordStages) {
        this.recordStages = recordStages;
    }

    public boolean isRecordTestResults() {
        return recordTestResults;
    }

    @DataBoundSetter
    public void setRecordTestResults(boolean recordTestResults) {
        this.recordTestResults = recordTestResults;
    }

    public boolean isRecordEnvironmentVariables() {
        return recordEnvironmentVariables;
    }

    @DataBoundSetter
    public void setRecordEnvironmentVariables(boolean recordEnvironmentVariables) {
        this.recordEnvironmentVariables = recordEnvironmentVariables;
    }

    public String getCustomFields() {
        return customFields;
    }

    @DataBoundSetter
    public void setCustomFields(String customFields) {
        this.customFields = customFields;
    }

    @Override
    public void perform(@NonNull Run<?, ?> run, @NonNull FilePath workspace, 
                       @NonNull EnvVars env, @NonNull Launcher launcher, 
                       @NonNull TaskListener listener) throws InterruptedException, IOException {
        
        listener.getLogger().println("[Supabase Build Recorder] Starting build data recording...");
        
        try {
            // Get the configured Supabase instance
            SupabaseEventTriggerConfiguration config = SupabaseEventTriggerConfiguration.get();
            SupabaseInstance instance = config.getInstanceByName(instanceName);
            
            if (instance == null) {
                listener.getLogger().println("[Supabase Build Recorder] ERROR: Supabase instance '" + instanceName + "' not found");
                return;
            }

            // Create the data client
            SupabaseDataClient dataClient = new SupabaseDataClient(instance, listener);
            
            // Note: Table creation is handled by SupabaseBuildRecorderJobListener when job config is saved
            // No need to initialize tables here - they should already exist
            
            // Convert EnvVars to Map for compatibility
            Map<String, String> envMap = new HashMap<>();
            if (env != null) {
                for (Map.Entry<String, String> entry : env.entrySet()) {
                    envMap.put(entry.getKey(), entry.getValue());
                }
            }
            
            // Record the build data
            dataClient.recordBuildData(run, workspace, envMap, this);
            
            listener.getLogger().println("[Supabase Build Recorder] Build data recorded successfully");
            
        } catch (Exception e) {
            listener.getLogger().println("[Supabase Build Recorder] ERROR: Failed to record build data: " + e.getMessage());
            LOGGER.severe("Failed to record build data for " + run.getFullDisplayName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * Gets the descriptor for this post-build action.
     * Required override to provide proper type safety for Jenkins descriptor system.
     */
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    @Symbol("supabaseBuildRecorder")
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        @SuppressWarnings("rawtypes") // AbstractProject requires raw type for Jenkins compatibility
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            // Only make Build Recorder available if it has been configured
            SupabaseEventTriggerConfiguration config = SupabaseEventTriggerConfiguration.get();
            return config != null && config.isBuildRecorderConfigured();
        }

        @Override
        public String getDisplayName() {
            return "Record Build Data to Supabase";
        }

        public ListBoxModel doFillInstanceNameItems() {
            ListBoxModel items = new ListBoxModel();
            SupabaseEventTriggerConfiguration config = SupabaseEventTriggerConfiguration.get();
            if (config != null && config.getSupabaseInstances() != null && !config.getSupabaseInstances().isEmpty()) {
                boolean first = true;
                for (SupabaseInstance instance : config.getSupabaseInstances()) {
                    if (first) {
                        items.add(instance.getName() + " (default)", instance.getName());
                        first = false;
                    } else {
                        items.add(instance.getName(), instance.getName());
                    }
                }
            } else {
                items.add("-- No Supabase Instances Configured --", "");
            }
            return items;
        }

        /**
         * Validates the Build Recorder configuration and provides helpful error messages.
         */
        public FormValidation doCheckConfiguration() {
            SupabaseEventTriggerConfiguration config = SupabaseEventTriggerConfiguration.get();
            
            if (config == null) {
                return FormValidation.error("Supabase plugin configuration not found. Please configure it in Manage Jenkins > System.");
            }
            
            if (!config.isBuildRecorderConfigured()) {
                return FormValidation.warning(
                    "Build Recorder is not configured. Please go to Manage Jenkins > System > Build Recorder Configuration and click 'Setup and Verify'."
                );
            }
            
            if (config.getSupabaseInstances() == null || config.getSupabaseInstances().isEmpty()) {
                return FormValidation.error("No Supabase instances configured. Please configure at least one instance in Manage Jenkins > System.");
            }
            
            return FormValidation.ok("Build Recorder is configured and ready.");
        }

        public FormValidation doCheckInstanceName(@QueryParameter String value) {
            if (value == null || value.trim().isEmpty()) {
                return FormValidation.error("Please select a Supabase instance. This field is required.");
            }
            SupabaseEventTriggerConfiguration config = SupabaseEventTriggerConfiguration.get();
            if (config == null || config.getInstanceByName(value) == null) {
                return FormValidation.error("Supabase instance '" + value + "' not found. Please configure it in Global Configuration.");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckCustomFields(@QueryParameter String value) {
            if (value != null && !value.trim().isEmpty()) {
                try {
                    // Try to parse as JSON to validate format
                    JSONObject.fromObject(value.trim());
                    return FormValidation.ok("Valid JSON format");
                } catch (Exception e) {
                    return FormValidation.error("Invalid JSON format: " + e.getMessage());
                }
            }
            return FormValidation.ok();
        }
    }
}