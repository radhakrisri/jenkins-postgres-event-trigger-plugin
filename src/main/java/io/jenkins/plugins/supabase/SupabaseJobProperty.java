package io.jenkins.plugins.supabase;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Job property that stores Supabase-related metadata for a job.
 * This metadata is recorded to the jobs table in Supabase when builds are recorded.
 */
public class SupabaseJobProperty extends JobProperty<Job<?, ?>> {

    private List<MetadataEntry> metadata;

    @DataBoundConstructor
    public SupabaseJobProperty() {
        this.metadata = new ArrayList<>();
    }

    public List<MetadataEntry> getMetadata() {
        return metadata;
    }

    @DataBoundSetter
    public void setMetadata(List<MetadataEntry> metadata) {
        this.metadata = metadata != null ? metadata : new ArrayList<>();
    }

    /**
     * Check if any metadata is configured.
     */
    public boolean hasMetadata() {
        return metadata != null && !metadata.isEmpty();
    }

    /**
     * Represents a single metadata key-value pair.
     */
    public static class MetadataEntry {
        private String key;
        private String value;

        @DataBoundConstructor
        public MetadataEntry(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }
    }

    @Extension
    @Symbol("supabaseJobProperty")
    public static final class DescriptorImpl extends JobPropertyDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Supabase Build Recorder Metadata";
        }

        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends Job> jobType) {
            return true;
        }

        public FormValidation doCheckValue(@QueryParameter String value, @QueryParameter String key) {
            if (value != null && !value.trim().isEmpty()) {
                // Try to parse as JSON to see if it's a valid JSON object/array
                try {
                    JSONObject.fromObject(value.trim());
                    return FormValidation.ok("Valid JSON object");
                } catch (Exception e1) {
                    // Not a JSON object, try as JSON array or primitive
                    try {
                        net.sf.json.JSONArray.fromObject(value.trim());
                        return FormValidation.ok("Valid JSON array");
                    } catch (Exception e2) {
                        // It's just a plain text value, which is fine
                        return FormValidation.ok("Text value");
                    }
                }
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckKey(@QueryParameter String value) {
            if (value == null || value.trim().isEmpty()) {
                return FormValidation.error("Key cannot be empty");
            }
            // Check for valid JSON key characters
            if (!value.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                return FormValidation.warning("Key should contain only alphanumeric characters and underscores, and start with a letter");
            }
            return FormValidation.ok();
        }
    }
}
