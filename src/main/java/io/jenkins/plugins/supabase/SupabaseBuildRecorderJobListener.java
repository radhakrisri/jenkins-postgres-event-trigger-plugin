package io.jenkins.plugins.supabase;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.model.listeners.ItemListener;

import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listener that automatically creates the builds table when a job with 
 * SupabaseBuildRecorder is saved/configured.
 * 
 * This ensures the table exists before the first build runs, following
 * the principle of "absolutely ZERO manual Supabase configuration".
 */
@Extension
public class SupabaseBuildRecorderJobListener extends ItemListener {
    
    private static final Logger LOGGER = Logger.getLogger(SupabaseBuildRecorderJobListener.class.getName());

    @Override
    public void onCreated(Item item) {
        handleJobConfiguration(item, "created");
    }

    @Override
    public void onUpdated(Item item) {
        handleJobConfiguration(item, "updated");
    }

    /**
     * Handle job configuration changes - create builds table if SupabaseBuildRecorder is configured.
     */
    private void handleJobConfiguration(Item item, String action) {
        if (!(item instanceof Job)) {
            return;
        }

        Job<?, ?> job = (Job<?, ?>) item;
        
        try {
            // Check if this job has SupabaseBuildRecorder configured
            SupabaseBuildRecorder recorder = getSupabaseBuildRecorder(job);
            if (recorder == null) {
                return; // No Build Recorder configured for this job
            }

            LOGGER.info("Job " + job.getFullName() + " " + action + " with SupabaseBuildRecorder - creating builds table");

            // Get the configured Supabase instance
            SupabaseEventTriggerConfiguration config = SupabaseEventTriggerConfiguration.get();
            if (config == null) {
                LOGGER.warning("SupabaseEventTriggerConfiguration not found");
                return;
            }

            SupabaseInstance instance = config.getInstanceByName(recorder.getInstanceName());
            if (instance == null) {
                LOGGER.warning("Supabase instance '" + recorder.getInstanceName() + "' not found");
                return;
            }

            // Create a task listener for logging
            TaskListener listener = new TaskListener() {
                @Override
                public PrintStream getLogger() {
                    return System.out;
                }
            };

            // Create the data client and initialize tables
            SupabaseDataClient dataClient = new SupabaseDataClient(instance, listener);
            
            // Get the schema from global configuration
            String schema = config.getBuildRecorderSchema();
            if (schema == null || schema.trim().isEmpty()) {
                schema = "public";
            }
            
            // Generate table name for this job
            String tableName = dataClient.generateTableName(job);
            
            // Create the builds table
            dataClient.createBuildTable(schema, tableName);
            
            // Wait for PostgREST schema cache to refresh
            dataClient.waitForSchemaCache();
            
            // Register job metadata (which also creates entry in jenkins_jobs table)
            dataClient.registerJobMetadata(job, tableName);
            
            LOGGER.info("Successfully created builds table '" + schema + "." + tableName + "' for job " + job.getFullName());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to create builds table for job " + job.getFullName(), e);
        }
    }

    /**
     * Extract SupabaseBuildRecorder from job configuration if present.
     */
    @SuppressWarnings("rawtypes")
    private SupabaseBuildRecorder getSupabaseBuildRecorder(Job<?, ?> job) {
        try {
            if (job instanceof hudson.model.AbstractProject) {
                hudson.model.AbstractProject project = (hudson.model.AbstractProject) job;
                hudson.util.DescribableList publishers = project.getPublishersList();
                
                if (publishers != null) {
                    for (Object publisher : publishers) {
                        if (publisher instanceof SupabaseBuildRecorder) {
                            return (SupabaseBuildRecorder) publisher;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error checking for SupabaseBuildRecorder in job " + job.getFullName(), e);
        }
        
        return null;
    }
}
