package io.jenkins.plugins.postgres;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;

public class SupabaseInstanceTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void testSupabaseInstanceCreation() {
        String name = "test-instance";
        String url = "https://test.supabase.co";
        String credentialsId = "test-cred-id";

        SupabaseInstance instance = new SupabaseInstance(name, url, credentialsId);

        assertEquals(name, instance.getName());
        assertEquals(url, instance.getUrl());
        assertEquals(credentialsId, instance.getCredentialsId());
    }

    @Test
    public void testSupabaseInstanceWithNullCredentials() {
        String name = "test-instance";
        String url = "https://test.supabase.co";

        SupabaseInstance instance = new SupabaseInstance(name, url, null);

        assertEquals(name, instance.getName());
        assertEquals(url, instance.getUrl());
        assertNull(instance.getCredentialsId());
    }
}
