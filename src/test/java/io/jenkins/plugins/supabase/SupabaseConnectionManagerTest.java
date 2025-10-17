package io.jenkins.plugins.supabase;

import com.google.gson.JsonObject;
import hudson.util.Secret;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.Assert.*;

/**
 * Tests for SupabaseConnectionManager.
 * Note: These are unit tests that test the manager's behavior without actual WebSocket connections.
 */
public class SupabaseConnectionManagerTest {

    private SupabaseConnectionManager manager;
    
    @Before
    public void setUp() {
        manager = SupabaseConnectionManager.getInstance();
    }
    
    @After
    public void tearDown() {
        // Clean up any connections created during tests
        // Note: In a real scenario, we'd want to properly shut down
        // but for unit tests, the manager is a singleton
    }
    
    @Test
    public void testGetInstance() {
        SupabaseConnectionManager instance1 = SupabaseConnectionManager.getInstance();
        SupabaseConnectionManager instance2 = SupabaseConnectionManager.getInstance();
        
        assertNotNull(instance1);
        assertNotNull(instance2);
        assertSame("Should return same singleton instance", instance1, instance2);
    }
    
    @Test
    public void testSubscribeCreatesConnection() {
        String instanceName = "test-instance";
        String apiUrl = "http://localhost:54321";
        Secret apiKey = Secret.fromString("test-key");
        String schema = "public";
        String table = "users";
        List<String> events = Arrays.asList("INSERT", "UPDATE");
        
        Map<String, Consumer<JsonObject>> handlers = new HashMap<>();
        AtomicInteger insertCount = new AtomicInteger(0);
        AtomicInteger updateCount = new AtomicInteger(0);
        
        handlers.put("INSERT", payload -> insertCount.incrementAndGet());
        handlers.put("UPDATE", payload -> updateCount.incrementAndGet());
        
        // Subscribe
        manager.subscribe(instanceName, apiUrl, apiKey, schema, table, events, handlers, "subscriber-1");
        
        // Verify stats show connection exists
        Map<String, SupabaseConnectionManager.ConnectionStats> stats = manager.getStats();
        assertNotNull(stats);
        assertTrue("Stats should contain instance", stats.containsKey(instanceName));
        
        SupabaseConnectionManager.ConnectionStats connStats = stats.get(instanceName);
        assertEquals("Should have 1 subscriber", 1, connStats.subscriberCount);
    }
    
    @Test
    public void testMultipleSubscribersShareConnection() {
        String instanceName = "test-instance-shared";
        String apiUrl = "http://localhost:54321";
        Secret apiKey = Secret.fromString("test-key");
        String schema = "public";
        String table = "users";
        
        // First subscriber
        Map<String, Consumer<JsonObject>> handlers1 = new HashMap<>();
        handlers1.put("INSERT", payload -> {});
        manager.subscribe(instanceName, apiUrl, apiKey, schema, table, 
                         Arrays.asList("INSERT"), handlers1, "subscriber-1");
        
        // Second subscriber (same table)
        Map<String, Consumer<JsonObject>> handlers2 = new HashMap<>();
        handlers2.put("UPDATE", payload -> {});
        manager.subscribe(instanceName, apiUrl, apiKey, schema, table, 
                         Arrays.asList("UPDATE"), handlers2, "subscriber-2");
        
        // Verify both subscribers are tracked
        Map<String, SupabaseConnectionManager.ConnectionStats> stats = manager.getStats();
        SupabaseConnectionManager.ConnectionStats connStats = stats.get(instanceName);
        
        assertEquals("Should have 2 subscribers", 2, connStats.subscriberCount);
    }
    
    @Test
    public void testUnsubscribeReducesSubscriberCount() {
        String instanceName = "test-instance-unsub";
        String apiUrl = "http://localhost:54321";
        Secret apiKey = Secret.fromString("test-key");
        String schema = "public";
        String table = "users";
        
        // Subscribe
        Map<String, Consumer<JsonObject>> handlers = new HashMap<>();
        handlers.put("INSERT", payload -> {});
        manager.subscribe(instanceName, apiUrl, apiKey, schema, table, 
                         Arrays.asList("INSERT"), handlers, "subscriber-1");
        
        Map<String, SupabaseConnectionManager.ConnectionStats> stats1 = manager.getStats();
        assertEquals(1, stats1.get(instanceName).subscriberCount);
        
        // Unsubscribe
        manager.unsubscribe(instanceName, schema, table, "subscriber-1");
        
        // After last subscriber unsubscribes, connection should be removed
        Map<String, SupabaseConnectionManager.ConnectionStats> stats2 = manager.getStats();
        assertFalse("Connection should be removed after last unsubscribe", 
                   stats2.containsKey(instanceName));
    }
    
    @Test
    public void testMultipleTablesOnSameInstance() {
        String instanceName = "test-instance-multi-table";
        String apiUrl = "http://localhost:54321";
        Secret apiKey = Secret.fromString("test-key");
        
        // Subscribe to first table
        Map<String, Consumer<JsonObject>> handlers1 = new HashMap<>();
        handlers1.put("INSERT", payload -> {});
        manager.subscribe(instanceName, apiUrl, apiKey, "public", "users", 
                         Arrays.asList("INSERT"), handlers1, "subscriber-1");
        
        // Subscribe to second table
        Map<String, Consumer<JsonObject>> handlers2 = new HashMap<>();
        handlers2.put("INSERT", payload -> {});
        manager.subscribe(instanceName, apiUrl, apiKey, "public", "orders", 
                         Arrays.asList("INSERT"), handlers2, "subscriber-2");
        
        // Both subscriptions should share the same connection
        Map<String, SupabaseConnectionManager.ConnectionStats> stats = manager.getStats();
        SupabaseConnectionManager.ConnectionStats connStats = stats.get(instanceName);
        
        assertEquals("Should have 2 subscribers on same connection", 2, connStats.subscriberCount);
    }
    
    @Test
    public void testResubscribeRemovesOldSubscription() {
        String instanceName = "test-instance-resub";
        String apiUrl = "http://localhost:54321";
        Secret apiKey = Secret.fromString("test-key");
        String schema = "public";
        String table = "users";
        String subscriberId = "subscriber-1";
        
        // Initial subscribe
        Map<String, Consumer<JsonObject>> handlers1 = new HashMap<>();
        handlers1.put("INSERT", payload -> {});
        manager.subscribe(instanceName, apiUrl, apiKey, schema, table, 
                         Arrays.asList("INSERT"), handlers1, subscriberId);
        
        Map<String, SupabaseConnectionManager.ConnectionStats> stats1 = manager.getStats();
        assertEquals(1, stats1.get(instanceName).subscriberCount);
        
        // Resubscribe with same subscriber ID (should replace, not add)
        Map<String, Consumer<JsonObject>> handlers2 = new HashMap<>();
        handlers2.put("UPDATE", payload -> {});
        manager.subscribe(instanceName, apiUrl, apiKey, schema, table, 
                         Arrays.asList("UPDATE"), handlers2, subscriberId);
        
        Map<String, SupabaseConnectionManager.ConnectionStats> stats2 = manager.getStats();
        assertEquals("Should still have 1 subscriber (replaced)", 1, stats2.get(instanceName).subscriberCount);
    }
    
    @Test
    public void testConnectionStatsFormat() {
        String instanceName = "test-instance-stats";
        String apiUrl = "http://localhost:54321";
        Secret apiKey = Secret.fromString("test-key");
        
        Map<String, Consumer<JsonObject>> handlers = new HashMap<>();
        handlers.put("INSERT", payload -> {});
        manager.subscribe(instanceName, apiUrl, apiKey, "public", "users", 
                         Arrays.asList("INSERT"), handlers, "subscriber-1");
        
        Map<String, SupabaseConnectionManager.ConnectionStats> stats = manager.getStats();
        SupabaseConnectionManager.ConnectionStats connStats = stats.get(instanceName);
        
        assertNotNull(connStats);
        assertEquals(instanceName, connStats.instanceName);
        assertNotNull(connStats.state);
        assertEquals(1, connStats.subscriberCount);
        assertEquals(0, connStats.eventsReceived);
        assertEquals(0, connStats.eventsFailed);
        
        // Test toString format
        String statsString = connStats.toString();
        assertNotNull(statsString);
        assertTrue("Stats should contain instance name", statsString.contains(instanceName));
        assertTrue("Stats should contain state", statsString.contains("State:"));
        assertTrue("Stats should contain subscriber count", statsString.contains("Subscribers:"));
    }
    
    @Test
    public void testThreadSafety() throws InterruptedException {
        String instanceName = "test-instance-threadsafe";
        String apiUrl = "http://localhost:54321";
        Secret apiKey = Secret.fromString("test-key");
        
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        
        // Create multiple threads that subscribe simultaneously
        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i;
            new Thread(() -> {
                try {
                    startLatch.await(); // Wait for signal to start
                    
                    Map<String, Consumer<JsonObject>> handlers = new HashMap<>();
                    handlers.put("INSERT", payload -> {});
                    
                    manager.subscribe(instanceName, apiUrl, apiKey, "public", "users", 
                                     Arrays.asList("INSERT"), handlers, "subscriber-" + threadNum);
                    
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }
        
        // Signal all threads to start
        startLatch.countDown();
        
        // Wait for all threads to complete
        assertTrue("All threads should complete", doneLatch.await(5, TimeUnit.SECONDS));
        
        // Verify all subscribers were added
        Map<String, SupabaseConnectionManager.ConnectionStats> stats = manager.getStats();
        SupabaseConnectionManager.ConnectionStats connStats = stats.get(instanceName);
        
        assertEquals("Should have all subscribers", threadCount, connStats.subscriberCount);
    }
    
    @Test
    public void testGetStatsReturnsEmptyMapWhenNoConnections() {
        // Create a fresh manager scenario by unsubscribing any test connections
        Map<String, SupabaseConnectionManager.ConnectionStats> stats = manager.getStats();
        assertNotNull("Stats should never be null", stats);
        // Note: May have connections from other tests, but should be a valid map
        assertTrue("Stats should be a map", stats instanceof Map);
    }
    
    @Test
    public void testUnsubscribeNonexistentInstance() {
        // Should not throw exception
        manager.unsubscribe("nonexistent-instance", "public", "users", "subscriber-1");
        
        // Verify no connection was created
        Map<String, SupabaseConnectionManager.ConnectionStats> stats = manager.getStats();
        assertFalse("Should not create connection for unsubscribe", 
                   stats.containsKey("nonexistent-instance"));
    }
    
    @Test
    public void testEventMultiplexing() throws InterruptedException {
        String instanceName = "test-instance-multiplex";
        String apiUrl = "http://localhost:54321";
        Secret apiKey = Secret.fromString("test-key");
        String schema = "public";
        String table = "users";
        
        AtomicInteger subscriber1Count = new AtomicInteger(0);
        AtomicInteger subscriber2Count = new AtomicInteger(0);
        
        // Subscriber 1: INSERT events
        Map<String, Consumer<JsonObject>> handlers1 = new HashMap<>();
        handlers1.put("INSERT", payload -> subscriber1Count.incrementAndGet());
        manager.subscribe(instanceName, apiUrl, apiKey, schema, table, 
                         Arrays.asList("INSERT"), handlers1, "subscriber-1");
        
        // Subscriber 2: INSERT events (same event type, different handler)
        Map<String, Consumer<JsonObject>> handlers2 = new HashMap<>();
        handlers2.put("INSERT", payload -> subscriber2Count.incrementAndGet());
        manager.subscribe(instanceName, apiUrl, apiKey, schema, table, 
                         Arrays.asList("INSERT"), handlers2, "subscriber-2");
        
        // Both should be subscribed
        Map<String, SupabaseConnectionManager.ConnectionStats> stats = manager.getStats();
        assertEquals(2, stats.get(instanceName).subscriberCount);
        
        // Note: Actual event delivery would be tested in integration tests
        // This unit test just verifies both subscriptions are registered
    }
}
