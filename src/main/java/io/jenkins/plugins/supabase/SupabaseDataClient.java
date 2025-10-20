package io.jenkins.plugins.supabase;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.FilePath;
import hudson.model.*;
import jenkins.model.Jenkins;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

/**
 * Client for interacting with Supabase database to record build data.
 */
public class SupabaseDataClient {

    private static final Logger LOGGER = Logger.getLogger(SupabaseDataClient.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final SupabaseInstance instance;
    private final TaskListener listener;

    public SupabaseDataClient(SupabaseInstance instance, TaskListener listener) {
        this.instance = instance;
        this.listener = listener;
    }

    /**
     * Initialize the necessary metadata for the job and create the builds table if needed.
     */
    public void initializeTables(Job<?, ?> job) throws IOException {
        String tableName = generateTableName(job);
        
        // Register/update job metadata
        registerJobMetadata(job, tableName);
        
        listener.getLogger().println("[Supabase] Job metadata registered for table: " + tableName);
        
        // Create the builds table automatically if it doesn't exist
        // Get the schema from global configuration
        SupabaseEventTriggerConfiguration globalConfig = SupabaseEventTriggerConfiguration.get();
        String schema = globalConfig != null ? globalConfig.getBuildRecorderSchema() : "public";
        if (schema == null || schema.trim().isEmpty()) {
            schema = "public";
        }
        
        try {
            createBuildTable(schema, tableName);
            listener.getLogger().println("[Supabase] Builds table created/verified: " + schema + "." + tableName);
        } catch (IOException e) {
            listener.getLogger().println("[Supabase] Warning: Could not create builds table via JDBC: " + e.getMessage());
            listener.getLogger().println("[Supabase] Table will be created automatically on first use");
        }
    }

    /**
     * Record comprehensive build data to Supabase.
     */
    public void recordBuildData(Run<?, ?> run, FilePath workspace, Map<String, String> env, 
                               SupabaseBuildRecorder recorder) throws IOException {
        String tableName = generateTableName(run.getParent());
        
        JsonObject buildData = collectBuildData(run, workspace, env, recorder);
        
        // Insert build data
        insertBuildRecord(tableName, buildData);
        
        listener.getLogger().println("[Supabase] Recorded build #" + run.getNumber() + 
                                   " to table '" + tableName + "'");
    }

    /**
     * Generate a sanitized table name based on the job path.
     * Public so it can be accessed by JobListener.
     */
    public String generateTableName(Job<?, ?> job) {
        String jobPath = job.getFullName();
        
        // Convert to lowercase and replace invalid characters
        String tableName = jobPath
            .toLowerCase()
            .replaceAll("[^a-z0-9_/]", "_")  // Replace invalid chars with underscore
            .replaceAll("/", "_")             // Replace folder separators
            .replaceAll("_{2,}", "_")         // Collapse multiple underscores
            .replaceAll("^_|_$", "");         // Remove leading/trailing underscores

        // Ensure it starts with a letter
        if (!tableName.matches("^[a-z].*")) {
            tableName = "job_" + tableName;
        }

        // Truncate if too long (PostgreSQL limit is 63 characters)
        if (tableName.length() > 50) {
            tableName = tableName.substring(0, 47) + "_" + Math.abs(jobPath.hashCode() % 100);
        }

        return "builds_" + tableName;
    }

    /**
     * Register or update job metadata using REST API.
     * Public so it can be accessed by JobListener.
     */
    public void registerJobMetadata(Job<?, ?> job, String tableName) throws IOException {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("job_name", job.getName());
        metadata.addProperty("job_full_name", job.getFullName());
        metadata.addProperty("job_display_name", job.getDisplayName());
        metadata.addProperty("table_name", tableName);
        metadata.addProperty("job_type", job.getClass().getSimpleName());
        
        String rootUrl = Jenkins.get().getRootUrl();
        if (rootUrl != null) {
            metadata.addProperty("job_url", rootUrl + job.getUrl());
        }
        
        // Extract folder path
        String folderPath = "";
        if (job.getParent() instanceof Jenkins) {
            folderPath = "/";
        } else {
            String fullName = job.getFullName();
            int lastSlash = fullName.lastIndexOf('/');
            if (lastSlash > 0) {
                folderPath = fullName.substring(0, lastSlash);
            }
        }
        metadata.addProperty("folder_path", folderPath);

        // Add configuration details
        JsonObject config = new JsonObject();
        config.addProperty("description", job.getDescription());
        
        // Add Supabase Job Property metadata if configured
        SupabaseJobProperty jobProperty = job.getProperty(SupabaseJobProperty.class);
        if (jobProperty != null && jobProperty.hasMetadata()) {
            for (SupabaseJobProperty.MetadataEntry entry : jobProperty.getMetadata()) {
                String key = entry.getKey();
                String value = entry.getValue();
                
                if (key != null && !key.trim().isEmpty() && value != null && !value.trim().isEmpty()) {
                    // Try to parse the value as JSON
                    try {
                        JsonElement parsedValue = JsonParser.parseString(value);
                        config.add(key, parsedValue);
                    } catch (Exception e) {
                        // If parsing fails, treat it as a plain string
                        config.addProperty(key, value);
                    }
                }
            }
        }
        
        metadata.add("configuration", config);

        // Use upsert to insert or update - use the configured job metadata table name
        SupabaseEventTriggerConfiguration globalConfig = SupabaseEventTriggerConfiguration.get();
        String jobMetadataTable = globalConfig != null ? globalConfig.getBuildRecorderJobTable() : "jenkins_jobs";
        if (jobMetadataTable == null || jobMetadataTable.trim().isEmpty()) {
            jobMetadataTable = "jenkins_jobs"; // fallback
        }
        upsertRecord(jobMetadataTable, metadata, "job_full_name");
    }

    /**
     * Collect comprehensive build data.
     */
    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "Jenkins.getRootUrl() and Run.getUrl() are checked for null")
    private JsonObject collectBuildData(Run<?, ?> run, FilePath workspace, Map<String, String> env, 
                                      SupabaseBuildRecorder recorder) {
        JsonObject data = new JsonObject();

        // Basic build information
        data.addProperty("build_number", run.getNumber());
        data.addProperty("build_id", run.getId());
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        String rootUrl = (jenkins != null && jenkins.getRootUrl() != null) ? jenkins.getRootUrl() : "http://localhost:8080/";
        data.addProperty("build_url", rootUrl + run.getUrl());
        data.addProperty("result", run.getResult() != null ? run.getResult().toString() : "UNKNOWN");
        data.addProperty("duration_ms", run.getDuration());
        
        // Times
        data.addProperty("start_time", formatTimestamp(run.getStartTimeInMillis()));
        if (run.getResult() != null) {
            data.addProperty("end_time", formatTimestamp(run.getStartTimeInMillis() + run.getDuration()));
        }
        
        // Queue information
        data.addProperty("queue_time_ms", run.getStartTimeInMillis() - run.getTimeInMillis());

        // Node and executor information
        JsonObject executorInfo = new JsonObject();
        Executor executor = run.getExecutor();
        if (executor != null) {
            Computer computer = executor.getOwner();
            executorInfo.addProperty("executor_number", executor.getNumber());
            executorInfo.addProperty("computer_name", computer.getName());
            executorInfo.addProperty("computer_display_name", computer.getDisplayName());
            data.addProperty("node_name", computer.getName());
        }
        data.add("executor_info", executorInfo);

        // Workspace
        if (workspace != null) {
            data.addProperty("workspace_path", workspace.getRemote());
        }

        // Build causes
        JsonObject causes = new JsonObject();
        List<Cause> buildCauses = run.getCauses();
        for (int i = 0; i < buildCauses.size(); i++) {
            Cause cause = buildCauses.get(i);
            causes.addProperty("cause_" + i + "_type", cause.getClass().getSimpleName());
            causes.addProperty("cause_" + i + "_description", cause.getShortDescription());
        }
        data.add("causes", causes);

        // Artifacts (if enabled)
        if (recorder.isRecordArtifacts()) {
            data.add("artifacts", collectArtifacts(run));
        }

        // Test results (if enabled)
        if (recorder.isRecordTestResults()) {
            data.add("test_results", collectTestResults(run));
        }

        // Stage information (if enabled and available)
        if (recorder.isRecordStages()) {
            data.add("stages", collectStageInfo(run));
        }

        // Environment variables (if enabled)
        if (recorder.isRecordEnvironmentVariables() && env != null) {
            JsonObject envVars = new JsonObject();
            for (Map.Entry<String, String> entry : env.entrySet()) {
                // Filter out sensitive variables
                String key = entry.getKey();
                if (!key.contains("PASSWORD") && !key.contains("SECRET") && !key.contains("TOKEN")) {
                    envVars.addProperty(key, entry.getValue());
                }
            }
            data.add("environment_variables", envVars);
        }

        // Custom fields
        if (recorder.getCustomFields() != null && !recorder.getCustomFields().trim().isEmpty()) {
            try {
                JsonObject customFields = GSON.fromJson(recorder.getCustomFields(), JsonObject.class);
                data.add("custom_data", customFields);
            } catch (Exception e) {
                LOGGER.warning("Failed to parse custom fields JSON: " + e.getMessage());
            }
        }

        return data;
    }

    @SuppressWarnings("rawtypes")
    private JsonObject collectArtifacts(Run<?, ?> run) {
        JsonObject artifacts = new JsonObject();
        List artifactList = run.getArtifacts();
        
        for (int i = 0; i < artifactList.size(); i++) {
            Object artifactObj = artifactList.get(i);
            if (artifactObj instanceof Run.Artifact) {
                Run.Artifact artifact = (Run.Artifact) artifactObj;
                JsonObject artifactInfo = new JsonObject();
                artifactInfo.addProperty("filename", artifact.getFileName());
                artifactInfo.addProperty("display_path", artifact.getDisplayPath());
                artifactInfo.addProperty("relative_path", artifact.relativePath);
                artifactInfo.addProperty("url", Jenkins.get().getRootUrl() + run.getUrl() + "artifact/" + artifact.relativePath);
                artifacts.add("artifact_" + i, artifactInfo);
            }
        }
        
        return artifacts;
    }

    private JsonObject collectTestResults(Run<?, ?> run) {
        JsonObject testResults = new JsonObject();
        
        // Simplified test result collection - would need junit plugin for full support
        try {
            @SuppressWarnings("deprecation")
            Action testAction = run.getAction(0); // This is a placeholder
            if (testAction != null && testAction.getClass().getSimpleName().contains("Test")) {
                testResults.addProperty("test_action_found", true);
                testResults.addProperty("test_action_type", testAction.getClass().getSimpleName());
            } else {
                testResults.addProperty("available", false);
            }
        } catch (Exception e) {
            testResults.addProperty("available", false);
            testResults.addProperty("error", "Test result collection requires additional plugins");
        }
        
        return testResults;
    }

    private JsonObject collectStageInfo(Run<?, ?> run) {
        JsonObject stages = new JsonObject();
        
        // This is a simplified version - full stage info would require workflow plugin integration
        String runClassName = run.getClass().getSimpleName();
        if (runClassName.contains("Workflow") || runClassName.contains("Pipeline")) {
            stages.addProperty("type", "pipeline");
            stages.addProperty("note", "Detailed stage information requires additional workflow plugin integration");
        } else {
            stages.addProperty("type", "freestyle");
        }
        
        return stages;
    }

    /**
     * Insert build record using REST API.
     */
    private void insertBuildRecord(String tableName, JsonObject buildData) throws IOException {
        insertRecord(tableName, buildData);
    }

    /**
     * Insert a record into a Supabase table using REST API.
     */
    private void insertRecord(String tableName, JsonObject data) throws IOException {
        String apiUrl = instance.getUrl() + "/rest/v1/" + tableName;
        
        try {
            URI uri = new URI(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("apikey", instance.getApiKey().getPlainText());
            connection.setRequestProperty("Authorization", "Bearer " + instance.getApiKey().getPlainText());
            connection.setRequestProperty("Prefer", "return=representation");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = GSON.toJson(data).getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 200 && responseCode != 201) {
                // Read error response
                String errorMsg = "HTTP " + responseCode;
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    if (response.length() > 0) {
                        errorMsg += ": " + response.toString();
                    }
                }
                throw new IOException("Failed to insert record into " + tableName + ": " + errorMsg);
            }
            
            LOGGER.fine("Successfully inserted record into " + tableName);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URL: " + apiUrl, e);
        }
    }

    /**
     * Upsert a record (insert or update on conflict) using REST API.
     * PostgREST upsert uses the Prefer header with "resolution=merge-duplicates"
     * and requires specifying the conflict columns via on_conflict query parameter.
     */
    private void upsertRecord(String tableName, JsonObject data, String conflictColumn) throws IOException {
        // Add on_conflict query parameter for upsert
        String apiUrl = instance.getUrl() + "/rest/v1/" + tableName + "?on_conflict=" + conflictColumn;
        
        try {
            URI uri = new URI(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("apikey", instance.getApiKey().getPlainText());
            connection.setRequestProperty("Authorization", "Bearer " + instance.getApiKey().getPlainText());
            // Use Prefer header for upsert behavior
            connection.setRequestProperty("Prefer", "resolution=merge-duplicates");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = GSON.toJson(data).getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 200 && responseCode != 201) {
                String errorMsg = "HTTP " + responseCode;
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    if (response.length() > 0) {
                        errorMsg += ": " + response.toString();
                    }
                }
                throw new IOException("Failed to upsert record into " + tableName + ": " + errorMsg);
            }
            
            LOGGER.fine("Successfully upserted record into " + tableName);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URL: " + apiUrl, e);
        }
    }

    /**
     * Format timestamp for PostgreSQL.
     */
    private String formatTimestamp(long millis) {
        return Instant.ofEpochMilli(millis)
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /**
     * Create the job metadata table in Supabase using JDBC.
     * This table stores information about all Jenkins jobs that use the Build Recorder.
     * Called during Build Recorder Configuration setup.
     */
    public void createJobMetadataTable(String schema, String tableName) throws IOException {
        listener.getLogger().println("[Supabase] Creating job metadata table: " + schema + "." + tableName);
        
        // Build the CREATE TABLE SQL
        String sql = String.format(
            "CREATE TABLE IF NOT EXISTS %s.%s (" +
            "  job_name TEXT NOT NULL," +
            "  job_full_name TEXT NOT NULL UNIQUE," +
            "  job_display_name TEXT," +
            "  table_name TEXT NOT NULL," +
            "  job_type TEXT," +
            "  job_url TEXT," +
            "  folder_path TEXT," +
            "  configuration JSONB," +
            "  created_at TIMESTAMPTZ DEFAULT NOW()," +
            "  updated_at TIMESTAMPTZ DEFAULT NOW()" +
            ")",
            schema, tableName
        );
        
        executeSqlViaJdbc(sql);
        listener.getLogger().println("[Supabase] Job metadata table created successfully");
    }

    /**
     * Create a build-specific table for a job using JDBC.
     * This table stores build data for all builds of a specific job.
     * Called when a job is configured with Build Recorder.
     */
    public void createBuildTable(String schema, String tableName) throws IOException {
        listener.getLogger().println("[Supabase] Creating build table: " + schema + "." + tableName);
        
        // Build the CREATE TABLE SQL
        String sql = String.format(
            "CREATE TABLE IF NOT EXISTS %s.%s (" +
            "  build_number INTEGER NOT NULL," +
            "  build_id TEXT," +
            "  build_url TEXT," +
            "  result TEXT," +
            "  duration_ms BIGINT," +
            "  start_time TIMESTAMPTZ," +
            "  end_time TIMESTAMPTZ," +
            "  queue_time_ms BIGINT," +
            "  node_name TEXT," +
            "  workspace_path TEXT," +
            "  executor_info JSONB," +
            "  causes JSONB," +
            "  artifacts JSONB," +
            "  test_results JSONB," +
            "  stages JSONB," +
            "  environment_variables JSONB," +
            "  custom_data JSONB," +
            "  created_at TIMESTAMPTZ DEFAULT NOW()," +
            "  PRIMARY KEY (build_number)" +
            ")",
            schema, tableName
        );
        
        executeSqlViaJdbc(sql);
        listener.getLogger().println("[Supabase] Build table created successfully");
    }

    /**
     * Execute SQL using JDBC connection to PostgreSQL.
     * This is the primary method for executing DDL statements.
     */
    private void executeSqlViaJdbc(String sql) throws IOException {
        String dbUrl = instance.getDbUrl();
        if (dbUrl == null || dbUrl.trim().isEmpty()) {
            throw new IOException("Database URL is not configured for this Supabase instance");
        }

        // Use the PostgreSQL driver directly from the plugin classloader
        // This bypasses DriverManager's classloader visibility issues
        try {
            ClassLoader pluginClassLoader = this.getClass().getClassLoader();
            Class<?> driverClass = Class.forName("org.postgresql.Driver", true, pluginClassLoader);
            java.sql.Driver driver = (java.sql.Driver) driverClass.getDeclaredConstructor().newInstance();
            
            // Connect directly using the driver instead of DriverManager
            java.util.Properties props = new java.util.Properties();
            String cleanUrl = dbUrl;
            
            // Parse URL to extract username and password if present in the URL
            // Format: postgresql://user:password@host:port/database
            // Need to convert to JDBC format: jdbc:postgresql://host:port/database
            if (dbUrl.contains("@")) {
                String userInfo = dbUrl.substring(dbUrl.indexOf("://") + 3, dbUrl.indexOf("@"));
                String hostAndDb = dbUrl.substring(dbUrl.indexOf("@") + 1); // "host:port/database"
                
                if (userInfo.contains(":")) {
                    String[] parts = userInfo.split(":", 2);
                    props.setProperty("user", parts[0]);
                    props.setProperty("password", parts[1]);
                }
                
                // Construct clean JDBC URL with jdbc: prefix
                cleanUrl = "jdbc:postgresql://" + hostAndDb;
            } else if (dbUrl.startsWith("postgresql://")) {
                // No credentials in URL, but need to add jdbc: prefix
                cleanUrl = "jdbc:" + dbUrl;
            }
            
            try (Connection conn = driver.connect(cleanUrl, props);
                 Statement stmt = conn.createStatement()) {
                
                stmt.execute(sql);
                LOGGER.fine("Successfully executed SQL via JDBC");
                
            }
        } catch (SQLException | ReflectiveOperationException e) {
            throw new IOException("Failed to execute SQL via JDBC: " + e.getMessage(), e);
        }
    }

    /**
     * Wait for PostgREST schema cache to refresh.
     * CRITICAL: After creating tables, PostgREST needs time to refresh its schema cache.
     * Without this wait, immediate API calls may fail with "relation does not exist" errors.
     */
    public void waitForSchemaCache() throws InterruptedException {
        listener.getLogger().println("[Supabase] Waiting for schema cache to refresh (3 seconds)...");
        Thread.sleep(3000); // 3 seconds wait
        listener.getLogger().println("[Supabase] Schema cache refresh complete");
    }

    /**
     * Verify that a table exists and is accessible via the REST API.
     * Returns true if the table exists, false otherwise.
     */
    public boolean verifyTableExists(String tableName) {
        String apiUrl = instance.getUrl() + "/rest/v1/" + tableName + "?limit=0";
        
        try {
            URI uri = new URI(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("apikey", instance.getApiKey().getPlainText());
            connection.setRequestProperty("Authorization", "Bearer " + instance.getApiKey().getPlainText());
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            connection.disconnect();
            
            return responseCode == 200;
        } catch (Exception e) {
            LOGGER.warning("Failed to verify table existence for " + tableName + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Ensure the build table exists for a job.
     * Creates the table if it doesn't exist, and waits for schema cache refresh.
     * This should be called before recording any build data.
     */
    public void ensureBuildTableExists(String schema, Job<?, ?> job) throws IOException, InterruptedException {
        String tableName = generateTableName(job);
        
        // Check if table already exists
        if (verifyTableExists(tableName)) {
            LOGGER.fine("Build table " + tableName + " already exists");
            return;
        }
        
        listener.getLogger().println("[Supabase] Build table does not exist, creating: " + tableName);
        
        // Create the build table
        createBuildTable(schema, tableName);
        
        // Register job metadata
        registerJobMetadata(job, tableName);
        
        // CRITICAL: Wait for schema cache to refresh
        waitForSchemaCache();
        
        // Verify table is now accessible
        if (!verifyTableExists(tableName)) {
            throw new IOException("Failed to verify table creation: " + tableName);
        }
        
        listener.getLogger().println("[Supabase] Build table ready: " + tableName);
    }
}