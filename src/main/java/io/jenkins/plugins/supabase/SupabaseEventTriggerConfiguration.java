package io.jenkins.plugins.supabase;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.Extension;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Extension
public class SupabaseEventTriggerConfiguration extends GlobalConfiguration {

    private List<SupabaseInstance> supabaseInstances;

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
}
