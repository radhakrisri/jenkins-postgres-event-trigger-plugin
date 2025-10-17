package io.jenkins.plugins.supabase;

import hudson.model.FreeStyleProject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Integration tests for SupabaseEventTrigger with SupabaseConnectionManager.
 * Tests real Jenkins job scenarios and connection management.
 */
public class SupabaseEventTriggerIntegrationTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();
    
    private SupabaseConnectionManager manager;
    private SupabaseEventTriggerConfiguration config;
    
    @Before
    public void setUp() {
        manager = SupabaseConnectionManager.getInstance();
        config = SupabaseEventTriggerConfiguration.get();
        if (config == null) {
            config = new SupabaseEventTriggerConfiguration();
        }
        
        // Set up test Supabase instance
        List<SupabaseInstance> instances = new ArrayList<>();
        instances.add(new SupabaseInstance("test-instance", "http://localhost:54321", "test-cred"));
        config.setSupabaseInstances(instances);
    }
    
    @After
    public void tearDown() {
        // Clean up any test connections
        // Note: In real scenario, proper cleanup would be needed
    }
    
    @Test
    public void testTriggerUsesConnectionManager() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("test-project");
        
        SupabaseEventTrigger trigger = new SupabaseEventTrigger("test-instance", "public.users");
        trigger.setSubscribeInsert(true);
        trigger.setSubscribeUpdate(true);
        
        // Add trigger to project
        project.addTrigger(trigger);
        
        // Start the trigger (simulates Jenkins starting the job)
        trigger.start(project, true);
        
        // Verify connection manager has the subscription
        Map<String, SupabaseConnectionManager.ConnectionStats> stats = manager.getStats();
        assertTrue("Should have connection for test-instance", stats.containsKey("test-instance"));
        
        SupabaseConnectionManager.ConnectionStats connStats = stats.get("test-instance");
        assertEquals("Should have 1 subscriber", 1, connStats.subscriberCount);
        
        // Stop the trigger
        trigger.stop();
        
        // After stopping, connection should be cleaned up (no more subscribers)
        Map<String, SupabaseConnectionManager.ConnectionStats> statsAfter = manager.getStats();
        if (statsAfter.containsKey("test-instance")) {
            assertEquals("Should have 0 subscribers after stop", 0, statsAfter.get("test-instance").subscriberCount);
        }
    }
    
    @Test
    public void testMultipleJobsShareConnection() throws Exception {
        // Create two jobs with triggers for same instance
        FreeStyleProject job1 = jenkins.createFreeStyleProject("job1");
        FreeStyleProject job2 = jenkins.createFreeStyleProject("job2");
        
        SupabaseEventTrigger trigger1 = new SupabaseEventTrigger("test-instance", "public.users");
        trigger1.setSubscribeInsert(true);
        job1.addTrigger(trigger1);
        
        SupabaseEventTrigger trigger2 = new SupabaseEventTrigger("test-instance", "public.orders");
        trigger2.setSubscribeUpdate(true);
        job2.addTrigger(trigger2);
        
        // Start both triggers
        trigger1.start(job1, true);
        trigger2.start(job2, true);
        
        // Verify both jobs share the same connection
        Map<String, SupabaseConnectionManager.ConnectionStats> stats = manager.getStats();
        assertTrue("Should have connection for test-instance", stats.containsKey("test-instance"));
        
        SupabaseConnectionManager.ConnectionStats connStats = stats.get("test-instance");
        assertEquals("Should have 2 subscribers sharing connection", 2, connStats.subscriberCount);
        
        // Stop first job
        trigger1.stop();
        
        // Second job should still be connected
        Map<String, SupabaseConnectionManager.ConnectionStats> statsAfter1 = manager.getStats();
        if (statsAfter1.containsKey("test-instance")) {
            assertEquals("Should have 1 subscriber remaining", 1, statsAfter1.get("test-instance").subscriberCount);
        }
        
        // Stop second job
        trigger2.stop();
        
        // No more subscribers, connection should be cleaned up
        Map<String, SupabaseConnectionManager.ConnectionStats> statsAfter2 = manager.getStats();
        if (statsAfter2.containsKey("test-instance")) {
            assertEquals("Should have 0 subscribers", 0, statsAfter2.get("test-instance").subscriberCount);
        }
    }
    
    @Test
    public void testJobsWithSameTableMultiplexEvents() throws Exception {
        // Create two jobs listening to same table but different events
        FreeStyleProject job1 = jenkins.createFreeStyleProject("insert-job");
        FreeStyleProject job2 = jenkins.createFreeStyleProject("update-job");
        
        SupabaseEventTrigger trigger1 = new SupabaseEventTrigger("test-instance", "public.users");
        trigger1.setSubscribeInsert(true);
        job1.addTrigger(trigger1);
        
        SupabaseEventTrigger trigger2 = new SupabaseEventTrigger("test-instance", "public.users");
        trigger2.setSubscribeUpdate(true);
        job2.addTrigger(trigger2);
        
        // Get baseline subscriber count
        int baselineCount = manager.getStats().containsKey("test-instance") ? 
                           manager.getStats().get("test-instance").subscriberCount : 0;
        
        // Start both triggers
        trigger1.start(job1, true);
        trigger2.start(job2, true);
        
        // Both should be on same connection
        Map<String, SupabaseConnectionManager.ConnectionStats> stats = manager.getStats();
        assertEquals("Should have added 2 subscribers on same connection", 
                    baselineCount + 2, stats.get("test-instance").subscriberCount);
        
        // Clean up
        trigger1.stop();
        trigger2.stop();
    }
    
    @Test
    public void testTriggerWithMultipleTables() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("multi-table-job");
        
        // Trigger listening to multiple tables
        SupabaseEventTrigger trigger = new SupabaseEventTrigger("test-instance", "public.users, public.orders, myschema.products");
        trigger.setSubscribeInsert(true);
        trigger.setSubscribeUpdate(true);
        project.addTrigger(trigger);
        
        // Get baseline subscriber count
        int baselineCount = manager.getStats().containsKey("test-instance") ? 
                           manager.getStats().get("test-instance").subscriberCount : 0;
        
        trigger.start(project, true);
        
        // Should create subscriptions for all 3 tables (each table counts as 1 subscriber entry in our implementation)
        Map<String, SupabaseConnectionManager.ConnectionStats> stats = manager.getStats();
        // Note: Each table subscription is tracked separately in the connection manager
        assertTrue("Should have increased subscriber count", 
                  stats.get("test-instance").subscriberCount > baselineCount);
        
        trigger.stop();
    }
    
    @Test 
    public void testTriggerRestart() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("restart-job");
        
        SupabaseEventTrigger trigger = new SupabaseEventTrigger("test-instance", "public.users");
        trigger.setSubscribeInsert(true);
        project.addTrigger(trigger);
        
        // Start trigger
        trigger.start(project, true);
        assertEquals(1, manager.getStats().get("test-instance").subscriberCount);
        
        // Stop trigger
        trigger.stop();
        
        // Restart trigger (simulates Jenkins restart)
        trigger.start(project, false); // newInstance = false for restart
        assertEquals("Should reconnect after restart", 
                    1, manager.getStats().get("test-instance").subscriberCount);
        
        trigger.stop();
    }
    
    @Test
    public void testUniqueSubscriberIds() throws Exception {
        // Create job with same name multiple times (simulates job reconfiguration)
        FreeStyleProject project = jenkins.createFreeStyleProject("same-name-job");
        
        SupabaseEventTrigger trigger1 = new SupabaseEventTrigger("test-instance", "public.users");
        trigger1.setSubscribeInsert(true);
        project.addTrigger(trigger1);
        
        // Get baseline
        int baselineCount = manager.getStats().containsKey("test-instance") ? 
                           manager.getStats().get("test-instance").subscriberCount : 0;
        
        trigger1.start(project, true);
        int afterFirstStart = manager.getStats().get("test-instance").subscriberCount;
        
        // Stop and create new trigger (simulates job reconfiguration)
        trigger1.stop();
        
        SupabaseEventTrigger trigger2 = new SupabaseEventTrigger("test-instance", "public.users");
        trigger2.setSubscribeInsert(true);
        project.addTrigger(trigger2);
        
        trigger2.start(project, true);
        
        // Should have similar subscriber count (one subscription active)
        Map<String, SupabaseConnectionManager.ConnectionStats> stats = manager.getStats();
        int afterSecondStart = stats.get("test-instance").subscriberCount;
        
        // The key test: starting and stopping should maintain reasonable subscriber count
        assertTrue("Subscriber count should be reasonable after restart", 
                  afterSecondStart >= baselineCount && afterSecondStart <= afterFirstStart + 1);
        
        trigger2.stop();
    }
    
    @Test
    public void testInvalidInstance() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("invalid-instance-job");
        
        // Trigger with non-existent instance
        SupabaseEventTrigger trigger = new SupabaseEventTrigger("nonexistent-instance", "public.users");
        trigger.setSubscribeInsert(true);
        project.addTrigger(trigger);
        
        // Should not throw exception, just log warning
        trigger.start(project, true);
        
        // Should not create any connections
        Map<String, SupabaseConnectionManager.ConnectionStats> stats = manager.getStats();
        assertFalse("Should not create connection for invalid instance", 
                   stats.containsKey("nonexistent-instance"));
        
        trigger.stop();
    }
    
    @Test
    public void testEmptyTablesConfiguration() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("empty-tables-job");
        
        // Trigger with empty tables
        SupabaseEventTrigger trigger = new SupabaseEventTrigger("test-instance", "");
        trigger.setSubscribeInsert(true);
        project.addTrigger(trigger);
        
        trigger.start(project, true);
        
        // Should not create subscriptions
        Map<String, SupabaseConnectionManager.ConnectionStats> stats = manager.getStats();
        if (stats.containsKey("test-instance")) {
            assertEquals("Should not create subscriptions for empty tables", 
                        0, stats.get("test-instance").subscriberCount);
        }
        
        trigger.stop();
    }
    
    @Test
    public void testNoEventsSelected() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("no-events-job");
        
        // Trigger with no events selected
        SupabaseEventTrigger trigger = new SupabaseEventTrigger("test-instance", "public.users");
        // Don't set any subscribe* flags (all false by default)
        project.addTrigger(trigger);
        
        trigger.start(project, true);
        
        // Should not create subscriptions
        Map<String, SupabaseConnectionManager.ConnectionStats> stats = manager.getStats();
        if (stats.containsKey("test-instance")) {
            assertEquals("Should not create subscriptions when no events selected", 
                        0, stats.get("test-instance").subscriberCount);
        }
        
        trigger.stop();
    }
}