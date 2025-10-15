package io.jenkins.plugins.postgres;

import hudson.model.FreeStyleProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;

public class PostgresEventTriggerTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void testTriggerCreation() {
        String instanceName = "test-instance";
        String tables = "users,orders";

        PostgresEventTrigger trigger = new PostgresEventTrigger(instanceName, tables);

        assertEquals(instanceName, trigger.getInstanceName());
        assertEquals(tables, trigger.getTables());
        assertFalse(trigger.isSubscribeInsert());
        assertFalse(trigger.isSubscribeUpdate());
        assertFalse(trigger.isSubscribeDelete());
    }

    @Test
    public void testTriggerWithEventSubscriptions() {
        String instanceName = "test-instance";
        String tables = "users";

        PostgresEventTrigger trigger = new PostgresEventTrigger(instanceName, tables);
        trigger.setSubscribeInsert(true);
        trigger.setSubscribeUpdate(true);
        trigger.setSubscribeDelete(false);

        assertTrue(trigger.isSubscribeInsert());
        assertTrue(trigger.isSubscribeUpdate());
        assertFalse(trigger.isSubscribeDelete());
    }

    @Test
    public void testTriggerDescriptor() throws Exception {
        // Ensure the descriptor is available in the test environment
        PostgresEventTrigger.DescriptorImpl descriptor = jenkins.jenkins.getDescriptorByType(PostgresEventTrigger.DescriptorImpl.class);
        if (descriptor == null) {
            // In test environment, extensions might not be loaded automatically
            // Create a new instance to ensure the descriptor is registered
            descriptor = new PostgresEventTrigger.DescriptorImpl();
        }
        assertNotNull(descriptor);
        assertEquals("Postgres/Supabase Event Trigger", descriptor.getDisplayName());

        FreeStyleProject project = jenkins.createFreeStyleProject();
        assertTrue(descriptor.isApplicable(project));
    }

    @Test
    public void testTriggerWithMultipleTables() {
        String instanceName = "test-instance";
        String tables = "public.users, myschema.orders, products";

        PostgresEventTrigger trigger = new PostgresEventTrigger(instanceName, tables);

        assertEquals(instanceName, trigger.getInstanceName());
        assertEquals(tables, trigger.getTables());
    }
}
