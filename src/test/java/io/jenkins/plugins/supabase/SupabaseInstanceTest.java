package io.jenkins.plugins.supabase;

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
        String dbUrl = "postgresql://postgres:postgres@localhost:54322/postgres";

        SupabaseInstance instance = new SupabaseInstance(name, url, credentialsId, dbUrl);

        assertEquals(name, instance.getName());
        assertEquals(url, instance.getUrl());
        assertEquals(credentialsId, instance.getCredentialsId());
        assertEquals(dbUrl, instance.getDbUrl());
    }

    @Test
    public void testSupabaseInstanceWithNullCredentials() {
        String name = "test-instance";
        String url = "https://test.supabase.co";
        String dbUrl = "postgresql://postgres:postgres@localhost:54322/postgres";

        SupabaseInstance instance = new SupabaseInstance(name, url, null, dbUrl);

        assertEquals(name, instance.getName());
        assertEquals(url, instance.getUrl());
        assertNull(instance.getCredentialsId());
        assertEquals(dbUrl, instance.getDbUrl());
    }
}
