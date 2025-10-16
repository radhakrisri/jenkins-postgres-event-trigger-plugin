package io.jenkins.plugins.postgres;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import hudson.FilePath;
import hudson.model.*;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
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
     * Initialize the necessary tables in Supabase for the job.
     */
    public void initializeTables(Job<?, ?> job) throws IOException {
        String tableName = generateTableName(job);
        
        // Create jobs metadata table if it doesn't exist
        createJobsMetadataTable();
        
        // Create or update job-specific table
        createJobBuildTable(tableName);
        
        // Register/update job metadata
        registerJobMetadata(job, tableName);
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
     * Generate a safe table name based on job path.
     */
    private String generateTableName(Job<?, ?> job) {
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
     * Create the jobs metadata table.
     */
    private void createJobsMetadataTable() throws IOException {
        String sql = """
            CREATE TABLE IF NOT EXISTS jobs (
                id SERIAL PRIMARY KEY,
                job_name TEXT NOT NULL UNIQUE,
                job_full_name TEXT NOT NULL,
                job_display_name TEXT,
                table_name TEXT NOT NULL,
                job_type TEXT,
                job_url TEXT,
                folder_path TEXT,
                created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                is_active BOOLEAN DEFAULT true,
                configuration JSONB,
                CONSTRAINT unique_job_full_name UNIQUE (job_full_name)
            );
            
            CREATE INDEX IF NOT EXISTS idx_jobs_job_name ON jobs(job_name);
            CREATE INDEX IF NOT EXISTS idx_jobs_table_name ON jobs(table_name);
            CREATE INDEX IF NOT EXISTS idx_jobs_is_active ON jobs(is_active);
            """;

        executeSQL(sql, "Failed to create jobs metadata table");
    }

    /**
     * Create the job-specific builds table.
     */
    private void createJobBuildTable(String tableName) throws IOException {
        String sql = String.format("""
            CREATE TABLE IF NOT EXISTS %s (
                id SERIAL PRIMARY KEY,
                build_number INTEGER NOT NULL,
                build_id TEXT NOT NULL,
                build_url TEXT,
                result TEXT,
                duration_ms BIGINT,
                start_time TIMESTAMP WITH TIME ZONE,
                end_time TIMESTAMP WITH TIME ZONE,
                queue_time_ms BIGINT,
                executor_info JSONB,
                node_name TEXT,
                workspace_path TEXT,
                
                -- Build causes and triggers
                causes JSONB,
                triggered_by TEXT,
                
                -- SCM information
                scm_info JSONB,
                
                -- Artifacts information
                artifacts JSONB,
                
                -- Test results
                test_results JSONB,
                
                -- Stage information (for pipeline builds)
                stages JSONB,
                
                -- Environment variables (if enabled)
                environment_variables JSONB,
                
                -- Custom fields
                custom_data JSONB,
                
                -- Metadata
                created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                
                CONSTRAINT unique_build_id_%s UNIQUE (build_id),
                CONSTRAINT unique_build_number_%s UNIQUE (build_number)
            );
            
            CREATE INDEX IF NOT EXISTS idx_%s_build_number ON %s(build_number DESC);
            CREATE INDEX IF NOT EXISTS idx_%s_result ON %s(result);
            CREATE INDEX IF NOT EXISTS idx_%s_start_time ON %s(start_time DESC);
            """, 
            tableName, 
            tableName.replaceAll("[^a-z0-9_]", ""), 
            tableName.replaceAll("[^a-z0-9_]", ""),
            tableName.replaceAll("[^a-z0-9_]", ""), tableName,
            tableName.replaceAll("[^a-z0-9_]", ""), tableName,
            tableName.replaceAll("[^a-z0-9_]", ""), tableName
        );

        executeSQL(sql, "Failed to create builds table: " + tableName);
    }

    /**
     * Register or update job metadata.
     */
    private void registerJobMetadata(Job<?, ?> job, String tableName) throws IOException {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("job_name", job.getName());
        metadata.addProperty("job_full_name", job.getFullName());
        metadata.addProperty("job_display_name", job.getDisplayName());
        metadata.addProperty("table_name", tableName);
        metadata.addProperty("job_type", job.getClass().getSimpleName());
        metadata.addProperty("job_url", Jenkins.get().getRootUrl() + job.getUrl());
        
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
        // Note: isDisabled() method might not be available in all Jenkins versions
        // config.addProperty("disabled", job.isDisabled());
        metadata.add("configuration", config);

        String sql = String.format("""
            INSERT INTO jobs (job_name, job_full_name, job_display_name, table_name, 
                            job_type, job_url, folder_path, configuration, updated_at)
            VALUES ('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s'::jsonb, NOW())
            ON CONFLICT (job_full_name) 
            DO UPDATE SET 
                job_display_name = EXCLUDED.job_display_name,
                job_url = EXCLUDED.job_url,
                configuration = EXCLUDED.configuration,
                updated_at = NOW(),
                is_active = true;
            """,
            escapeSQL(job.getName()),
            escapeSQL(job.getFullName()),
            escapeSQL(job.getDisplayName()),
            escapeSQL(tableName),
            escapeSQL(job.getClass().getSimpleName()),
            escapeSQL(Jenkins.get().getRootUrl() + job.getUrl()),
            escapeSQL(folderPath),
            escapeSQL(GSON.toJson(config))
        );

        executeSQL(sql, "Failed to register job metadata");
    }

    /**
     * Collect comprehensive build data.
     */
    private JsonObject collectBuildData(Run<?, ?> run, FilePath workspace, Map<String, String> env, 
                                      SupabaseBuildRecorder recorder) {
        JsonObject data = new JsonObject();

        // Basic build information
        data.addProperty("build_number", run.getNumber());
        data.addProperty("build_id", run.getId());
        data.addProperty("build_url", Jenkins.get().getRootUrl() + run.getUrl());
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
     * Insert build record into the database.
     */
    private void insertBuildRecord(String tableName, JsonObject buildData) throws IOException {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(tableName).append(" (");
        
        List<String> columns = new ArrayList<>();
        List<String> values = new ArrayList<>();
        
        for (Map.Entry<String, com.google.gson.JsonElement> entry : buildData.entrySet()) {
            columns.add(entry.getKey());
            if (entry.getValue().isJsonObject() || entry.getValue().isJsonArray()) {
                values.add("'" + escapeSQL(entry.getValue().toString()) + "'::jsonb");
            } else if (entry.getValue().isJsonNull()) {
                values.add("NULL");
            } else {
                values.add("'" + escapeSQL(entry.getValue().getAsString()) + "'");
            }
        }
        
        sql.append(String.join(", ", columns));
        sql.append(") VALUES (");
        sql.append(String.join(", ", values));
        sql.append(");");

        executeSQL(sql.toString(), "Failed to insert build record");
    }

    /**
     * Execute SQL against Supabase.
     */
    private void executeSQL(String sql, String errorMessage) throws IOException {
        String apiUrl = instance.getUrl() + "/rest/v1/rpc/execute_sql";
        
        JsonObject payload = new JsonObject();
        payload.addProperty("sql", sql);

        try {
            URI uri = new URI(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("apikey", instance.getApiKey().getPlainText());
            connection.setRequestProperty("Authorization", "Bearer " + instance.getApiKey().getPlainText());
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 200 && responseCode != 201) {
                throw new IOException(errorMessage + ": HTTP " + responseCode);
            }
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
     * Escape SQL string values.
     */
    private String escapeSQL(String value) {
        if (value == null) return "";
        return value.replace("'", "''");
    }
}