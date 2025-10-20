package io.jenkins.plugins.supabase;

import jenkins.model.GlobalConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SupabaseEventTriggerConfigurationTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @SuppressWarnings("deprecation")
    private SupabaseEventTriggerConfiguration getConfiguration() {
        SupabaseEventTriggerConfiguration config = SupabaseEventTriggerConfiguration.get();
        if (config == null) {
            // If not auto-loaded, create manually for test
            config = new SupabaseEventTriggerConfiguration();
            GlobalConfiguration.all().add(config);
        }
        return config;
    }

    @Test
    public void testGlobalConfigurationExists() {
        SupabaseEventTriggerConfiguration config = getConfiguration();
        assertNotNull(config);
    }

    @Test
    public void testSetAndGetSupabaseInstances() {
        SupabaseEventTriggerConfiguration config = getConfiguration();
        
        List<SupabaseInstance> instances = new ArrayList<>();
        instances.add(new SupabaseInstance("instance1", "https://test1.supabase.co", "cred1", "postgresql://postgres:postgres@localhost:54322/postgres"));
        instances.add(new SupabaseInstance("instance2", "https://test2.supabase.co", "cred2", "postgresql://postgres:postgres@localhost:54322/postgres"));
        
        config.setSupabaseInstances(instances);
        
        List<SupabaseInstance> retrieved = config.getSupabaseInstances();
        assertNotNull(retrieved);
        assertEquals(2, retrieved.size());
        assertEquals("instance1", retrieved.get(0).getName());
        assertEquals("instance2", retrieved.get(1).getName());
    }

    @Test
    public void testGetInstanceByName() {
        SupabaseEventTriggerConfiguration config = getConfiguration();
        
        List<SupabaseInstance> instances = new ArrayList<>();
        instances.add(new SupabaseInstance("instance1", "https://test1.supabase.co", "cred1", "postgresql://postgres:postgres@localhost:54322/postgres"));
        instances.add(new SupabaseInstance("instance2", "https://test2.supabase.co", "cred2", "postgresql://postgres:postgres@localhost:54322/postgres"));
        
        config.setSupabaseInstances(instances);
        
        SupabaseInstance found = config.getInstanceByName("instance1");
        assertNotNull(found);
        assertEquals("instance1", found.getName());
        assertEquals("https://test1.supabase.co", found.getUrl());
        
        SupabaseInstance notFound = config.getInstanceByName("nonexistent");
        assertNull(notFound);
    }

    @Test
    public void testGetInstanceNames() {
        SupabaseEventTriggerConfiguration config = getConfiguration();
        
        List<SupabaseInstance> instances = new ArrayList<>();
        instances.add(new SupabaseInstance("instance1", "https://test1.supabase.co", "cred1", "postgresql://postgres:postgres@localhost:54322/postgres"));
        instances.add(new SupabaseInstance("instance2", "https://test2.supabase.co", "cred2", "postgresql://postgres:postgres@localhost:54322/postgres"));
        
        config.setSupabaseInstances(instances);
        
        List<String> names = config.getInstanceNames();
        assertNotNull(names);
        assertEquals(2, names.size());
        assertTrue(names.contains("instance1"));
        assertTrue(names.contains("instance2"));
    }
}
