package io.jenkins.plugins.postgres;

import hudson.Extension;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Extension
public class PostgresEventTriggerConfiguration extends GlobalConfiguration {

    private List<SupabaseInstance> supabaseInstances;

    public PostgresEventTriggerConfiguration() {
        load();
    }

    public static PostgresEventTriggerConfiguration get() {
        return GlobalConfiguration.all().get(PostgresEventTriggerConfiguration.class);
    }

    public List<SupabaseInstance> getSupabaseInstances() {
        return supabaseInstances;
    }

    @DataBoundSetter
    public void setSupabaseInstances(List<SupabaseInstance> supabaseInstances) {
        this.supabaseInstances = supabaseInstances;
        save();
    }

    @Override
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
}
