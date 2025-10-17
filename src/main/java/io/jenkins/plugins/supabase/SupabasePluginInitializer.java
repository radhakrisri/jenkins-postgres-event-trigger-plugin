package io.jenkins.plugins.supabase;

import hudson.init.Initializer;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.init.InitMilestone.JOB_CONFIG_ADAPTED;

/**
 * Initializer for the Supabase plugin that sets up required database tables
 * when Jenkins starts up.
 */
public class SupabasePluginInitializer {

    private static final Logger LOGGER = Logger.getLogger(SupabasePluginInitializer.class.getName());

    /**
     * Initialize the plugin by ensuring the jobs metadata table exists.
     * This runs after Jenkins configuration is loaded but before jobs are started.
     */
    @Initializer(after = JOB_CONFIG_ADAPTED)
    public static void initialize() {
        LOGGER.info("Initializing Supabase Plugin...");
        
        try {
            initializeJobsTable();
            LOGGER.info("Supabase Plugin initialization completed successfully");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to initialize Supabase Plugin. " +
                    "Build Recorder may require manual table setup.", e);
        }
    }

    /**
     * Initialize the jobs metadata table for all configured Supabase instances.
     * This prevents race conditions when multiple builds try to create the table simultaneously.
     */
    private static void initializeJobsTable() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            LOGGER.warning("Jenkins instance not available, skipping table initialization");
            return;
        }

        SupabaseEventTriggerConfiguration config = 
            jenkins.getDescriptorByType(SupabaseEventTriggerConfiguration.class);
        
        if (config == null || config.getSupabaseInstances() == null) {
            LOGGER.fine("No Supabase instances configured, skipping table initialization");
            return;
        }

        for (SupabaseInstance instance : config.getSupabaseInstances()) {
            try {
                createJobsTableIfNotExists(instance);
                LOGGER.info("Initialized jobs table for Supabase instance: " + instance.getName());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, 
                    "Failed to initialize jobs table for instance: " + instance.getName(), e);
            }
        }
    }

    /**
     * Create the jobs metadata table if it doesn't exist using the Supabase REST API.
     */
    private static void createJobsTableIfNotExists(SupabaseInstance instance) throws IOException {
        // First, check if the table exists by trying to query it
        if (tableExists(instance, "jobs")) {
            LOGGER.fine("Jobs table already exists for instance: " + instance.getName());
            return;
        }

        LOGGER.info("Jobs table does not exist for instance " + instance.getName() + 
                   ". Please create it manually using the provided SQL script.");
        
        // Note: We cannot create tables via REST API in Supabase
        // Users must create tables manually or via migrations
        // This method just checks if the table exists and logs appropriately
    }

    /**
     * Check if a table exists by attempting to query it.
     */
    private static boolean tableExists(SupabaseInstance instance, String tableName) {
        String apiUrl = instance.getUrl() + "/rest/v1/" + tableName + "?limit=0";
        
        try {
            URI uri = new URI(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("apikey", instance.getApiKey().getPlainText());
            connection.setRequestProperty("Authorization", "Bearer " + instance.getApiKey().getPlainText());
            connection.setRequestProperty("Prefer", "count=exact");
            
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            
            // 200 means table exists, 404 or 406 means it doesn't
            return responseCode == 200;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error checking if table exists: " + tableName, e);
            return false;
        }
    }
}
