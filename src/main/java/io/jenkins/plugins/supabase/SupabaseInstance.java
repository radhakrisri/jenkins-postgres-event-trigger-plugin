package io.jenkins.plugins.supabase;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.Serializable;

public class SupabaseInstance extends AbstractDescribableImpl<SupabaseInstance> implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final String name;
    private final String url;
    private final String credentialsId;

    @DataBoundConstructor
    public SupabaseInstance(String name, String url, String credentialsId) {
        this.name = name;
        this.url = url;
        this.credentialsId = credentialsId;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @SuppressWarnings("deprecation")
    public Secret getApiKey() {
        if (credentialsId == null || credentialsId.isEmpty()) {
            return null;
        }
        
        StringCredentials credentials = CredentialsMatchers.firstOrNull(
            CredentialsProvider.lookupCredentials(
                StringCredentials.class,
                Jenkins.get()
            ),
            CredentialsMatchers.withId(credentialsId)
        );
        
        if (credentials != null) {
            return credentials.getSecret();
        }
        return null;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<SupabaseInstance> {
        
        @Override
        public String getDisplayName() {
            return "Supabase Instance";
        }

        @SuppressWarnings("deprecation")
        public ListBoxModel doFillCredentialsIdItems(@QueryParameter String credentialsId) {
            if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                return new StandardListBoxModel().includeCurrentValue(credentialsId);
            }
            
            return new StandardListBoxModel()
                .includeEmptyValue()
                .includeAs(ACL.SYSTEM, Jenkins.get(), StringCredentials.class);
        }
    }
}
