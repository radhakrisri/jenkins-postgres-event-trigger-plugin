package io.jenkins.plugins.supabase;

import com.google.gson.JsonObject;
import hudson.util.Secret;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages WebSocket connections to Supabase Realtime instances.
 * Implements connection pooling to share one connection per Supabase instance
 * across multiple Jenkins jobs, with automatic reconnection and health monitoring.
 * 
 * Thread-safe singleton pattern.
 */
public class SupabaseConnectionManager {
    
    private static final Logger LOGGER = Logger.getLogger(SupabaseConnectionManager.class.getName());
    private static final SupabaseConnectionManager INSTANCE = new SupabaseConnectionManager();
    
    // Connection pool: instance name -> managed connection
    private final ConcurrentHashMap<String, ManagedConnection> connections = new ConcurrentHashMap<>();
    
    // Executor for reconnection attempts and heartbeat
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
        2, 
        r -> {
            Thread t = new Thread(r, "SupabaseConnectionManager");
            t.setDaemon(true);
            return t;
        }
    );
    
    private SupabaseConnectionManager() {
        // Private constructor for singleton
    }
    
    public static SupabaseConnectionManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Subscribe to table events. Creates or reuses existing connection.
     * 
     * @param instanceName The Supabase instance name
     * @param apiUrl The Supabase API URL
     * @param apiKey The API key
     * @param schema Database schema
     * @param table Table name
     * @param events List of event types (INSERT, UPDATE, DELETE)
     * @param handlers Map of event type to handler function
     * @param subscriberId Unique ID for this subscriber (job name)
     */
    public void subscribe(String instanceName, String apiUrl, Secret apiKey,
                         String schema, String table, List<String> events,
                         Map<String, Consumer<JsonObject>> handlers,
                         String subscriberId) {
        
        ManagedConnection connection = connections.computeIfAbsent(instanceName, 
            k -> new ManagedConnection(instanceName, apiUrl, apiKey, scheduler));
        
        connection.subscribe(schema, table, events, handlers, subscriberId);
    }
    
    /**
     * Unsubscribe from table events and clean up if no more subscribers.
     * 
     * @param instanceName The Supabase instance name
     * @param schema Database schema
     * @param table Table name
     * @param subscriberId Unique ID for this subscriber
     */
    public void unsubscribe(String instanceName, String schema, String table, String subscriberId) {
        ManagedConnection connection = connections.get(instanceName);
        if (connection != null) {
            connection.unsubscribe(schema, table, subscriberId);
            
            // Clean up if no more subscribers
            if (connection.getSubscriberCount() == 0) {
                connection.close();
                connections.remove(instanceName);
                LOGGER.info("Removed connection for instance: " + instanceName + " (no subscribers)");
            }
        }
    }
    
    /**
     * Get connection statistics for monitoring.
     */
    public Map<String, ConnectionStats> getStats() {
        Map<String, ConnectionStats> stats = new HashMap<>();
        connections.forEach((name, conn) -> stats.put(name, conn.getStats()));
        return stats;
    }
    
    /**
     * Shutdown all connections gracefully.
     */
    public void shutdown() {
        LOGGER.info("Shutting down SupabaseConnectionManager");
        connections.values().forEach(ManagedConnection::close);
        connections.clear();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Represents a managed WebSocket connection with reconnection logic.
     */
    private static class ManagedConnection {
        private final String instanceName;
        private final String apiUrl;
        private final Secret apiKey;
        private final ScheduledExecutorService scheduler;
        
        private SupabaseRealtimeClient client;
        private volatile ConnectionState state = ConnectionState.DISCONNECTED;
        
        // Subscription tracking: "schema.table" -> list of subscribers
        private final ConcurrentHashMap<String, List<Subscription>> subscriptions = new ConcurrentHashMap<>();
        
        // Reconnection parameters
        private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
        private ScheduledFuture<?> reconnectTask;
        private ScheduledFuture<?> heartbeatTask;
        
        // Statistics
        private volatile long connectedSince = 0;
        private final AtomicInteger eventsReceived = new AtomicInteger(0);
        private final AtomicInteger eventsFailed = new AtomicInteger(0);
        private final AtomicInteger reconnections = new AtomicInteger(0);
        
        public ManagedConnection(String instanceName, String apiUrl, Secret apiKey, 
                                ScheduledExecutorService scheduler) {
            this.instanceName = instanceName;
            this.apiUrl = apiUrl;
            this.apiKey = apiKey;
            this.scheduler = scheduler;
            connect();
        }
        
        private void connect() {
            if (state == ConnectionState.CONNECTING) {
                return; // Already connecting
            }
            
            state = ConnectionState.CONNECTING;
            LOGGER.info("Connecting to Supabase instance: " + instanceName);
            
            try {
                String wsUrl = buildWebSocketUrl(apiUrl, apiKey.getPlainText());
                client = new SupabaseRealtimeClient(wsUrl, null);
                
                // Set up callbacks
                client.setOnOpenCallback(this::onConnectionOpened);
                client.setOnCloseCallback(this::onConnectionClosedCallback);
                client.setOnErrorCallback(this::onConnectionError);
                
                client.connect();
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to create WebSocket client for " + instanceName, e);
                state = ConnectionState.ERROR;
                scheduleReconnect();
            }
        }
        
        private void onConnectionOpened() {
            LOGGER.info("Connected to Supabase instance: " + instanceName);
            state = ConnectionState.CONNECTED;
            connectedSince = System.currentTimeMillis();
            reconnectAttempts.set(0);
            
            // Resubscribe to all channels
            resubscribeAll();
            
            // Start heartbeat
            startHeartbeat();
            
            // Track reconnection (if not first connection)
            if (reconnections.get() > 0 || connectedSince > 0) {
                reconnections.incrementAndGet();
            }
        }
        
        private void onConnectionClosed(int code, String reason, boolean remote) {
            LOGGER.warning("Disconnected from " + instanceName + ": " + reason + " (code: " + code + ")");
            state = ConnectionState.DISCONNECTED;
            
            // Stop heartbeat
            if (heartbeatTask != null) {
                heartbeatTask.cancel(false);
                heartbeatTask = null;
            }
            
            // Attempt reconnection if we have subscribers
            if (getSubscriberCount() > 0 && remote) {
                scheduleReconnect();
            }
        }
        
        private void onConnectionClosedCallback(String closeInfo) {
            // Parse "code:reason:remote"
            String[] parts = closeInfo.split(":", 3);
            int code = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
            String reason = parts.length > 1 ? parts[1] : "";
            boolean remote = parts.length > 2 && Boolean.parseBoolean(parts[2]);
            onConnectionClosed(code, reason, remote);
        }
        
        private void onConnectionError(Exception ex) {
            LOGGER.log(Level.WARNING, "Connection error for " + instanceName, ex);
            eventsFailed.incrementAndGet();
        }
        
        private void scheduleReconnect() {
            if (reconnectTask != null && !reconnectTask.isDone()) {
                return; // Already scheduled
            }
            
            int attempts = reconnectAttempts.incrementAndGet();
            
            // Max reconnection attempts: 10
            if (attempts > 10) {
                LOGGER.severe("Max reconnection attempts reached for " + instanceName + ". Giving up.");
                state = ConnectionState.ERROR;
                return;
            }
            
            // Exponential backoff: 1s, 2s, 4s, 8s, 16s, max 30s
            long delaySeconds = Math.min(30, (long) Math.pow(2, attempts - 1));
            
            LOGGER.info(String.format("Scheduling reconnection attempt %d for %s in %d seconds", 
                                     attempts, instanceName, delaySeconds));
            
            reconnectTask = scheduler.schedule(this::connect, delaySeconds, TimeUnit.SECONDS);
        }
        
        private void resubscribeAll() {
            LOGGER.info("Resubscribing to " + subscriptions.size() + " channels");
            
            subscriptions.forEach((key, subs) -> {
                if (subs.isEmpty()) {
                    return;
                }
                
                // Get schema and table from first subscription
                Subscription firstSub = subs.get(0);
                String schema = firstSub.schema;
                String table = firstSub.table;
                
                // Collect all unique events across subscribers
                Set<String> allEvents = new HashSet<>();
                Map<String, List<Consumer<JsonObject>>> handlersByEvent = new HashMap<>();
                
                for (Subscription sub : subs) {
                    allEvents.addAll(sub.events);
                    for (Map.Entry<String, Consumer<JsonObject>> entry : sub.handlers.entrySet()) {
                        handlersByEvent.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                                      .add(entry.getValue());
                    }
                }
                
                // Create multiplexing handlers
                Map<String, Consumer<JsonObject>> multiplexHandlers = new HashMap<>();
                for (Map.Entry<String, List<Consumer<JsonObject>>> entry : handlersByEvent.entrySet()) {
                    String event = entry.getKey();
                    List<Consumer<JsonObject>> handlers = entry.getValue();
                    
                    multiplexHandlers.put(event, payload -> {
                        eventsReceived.incrementAndGet();
                        for (Consumer<JsonObject> handler : handlers) {
                            try {
                                handler.accept(payload);
                            } catch (Exception e) {
                                LOGGER.log(Level.SEVERE, "Error in event handler", e);
                                eventsFailed.incrementAndGet();
                            }
                        }
                    });
                }
                
                // Subscribe once with all events
                try {
                    client.subscribeToTableEvents(schema, table, new ArrayList<>(allEvents), multiplexHandlers);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Failed to resubscribe to " + key, e);
                }
            });
        }
        
        private void startHeartbeat() {
            heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
                try {
                    if (client != null && client.isOpen()) {
                        client.sendHeartbeat();
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error sending heartbeat", e);
                }
            }, 30, 30, TimeUnit.SECONDS);
        }
        
        public void subscribe(String schema, String table, List<String> events,
                            Map<String, Consumer<JsonObject>> handlers, String subscriberId) {
            
            String key = schema + "." + table;
            
            subscriptions.compute(key, (k, existing) -> {
                List<Subscription> subs = existing != null ? existing : new CopyOnWriteArrayList<>();
                
                // Check if this subscriber already exists
                subs.removeIf(s -> s.subscriberId.equals(subscriberId));
                
                // Add new subscription
                subs.add(new Subscription(subscriberId, schema, table, events, handlers));
                
                return subs;
            });
            
            // If connected, subscribe now
            if (state == ConnectionState.CONNECTED) {
                resubscribeAll();
            }
            
            LOGGER.info(String.format("Subscriber %s subscribed to %s.%s for events %s", 
                                     subscriberId, schema, table, events));
        }
        
        public void unsubscribe(String schema, String table, String subscriberId) {
            String key = schema + "." + table;
            
            subscriptions.computeIfPresent(key, (k, subs) -> {
                subs.removeIf(s -> s.subscriberId.equals(subscriberId));
                
                // If no more subscribers for this table, unsubscribe from Supabase
                if (subs.isEmpty()) {
                    if (client != null && client.isOpen()) {
                        try {
                            client.unsubscribeFromTable(schema, table);
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Error unsubscribing from " + key, e);
                        }
                    }
                    return null; // Remove entry
                }
                
                // Otherwise, resubscribe with remaining subscribers
                if (state == ConnectionState.CONNECTED) {
                    resubscribeAll();
                }
                
                return subs;
            });
            
            LOGGER.info(String.format("Subscriber %s unsubscribed from %s.%s", subscriberId, schema, table));
        }
        
        public int getSubscriberCount() {
            return subscriptions.values().stream()
                               .mapToInt(List::size)
                               .sum();
        }
        
        public void close() {
            LOGGER.info("Closing connection to " + instanceName);
            state = ConnectionState.DISCONNECTED;
            
            if (heartbeatTask != null) {
                heartbeatTask.cancel(false);
            }
            if (reconnectTask != null) {
                reconnectTask.cancel(false);
            }
            
            if (client != null && client.isOpen()) {
                // Unsubscribe from all channels
                subscriptions.forEach((key, subs) -> {
                    String[] parts = key.split("\\.", 2);
                    if (parts.length == 2) {
                        try {
                            client.unsubscribeFromTable(parts[0], parts[1]);
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Error unsubscribing during close", e);
                        }
                    }
                });
                
                client.close();
            }
            
            subscriptions.clear();
        }
        
        public ConnectionStats getStats() {
            return new ConnectionStats(
                instanceName,
                state.name(),
                connectedSince,
                eventsReceived.get(),
                eventsFailed.get(),
                reconnections.get(),
                getSubscriberCount()
            );
        }
        
        private String buildWebSocketUrl(String apiUrl, String apiKeyPlain) {
            if (apiUrl.endsWith("/")) {
                apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
            }
            String wsUrl = apiUrl.replace("https://", "wss://").replace("http://", "ws://");
            return wsUrl + "/realtime/v1/websocket?apikey=" + apiKeyPlain + "&vsn=1.0.0";
        }
    }
    
    /**
     * Represents a subscription to a table by a specific job.
     */
    private static class Subscription {
        final String subscriberId;
        final String schema;
        final String table;
        final List<String> events;
        final Map<String, Consumer<JsonObject>> handlers;
        
        Subscription(String subscriberId, String schema, String table, 
                    List<String> events, Map<String, Consumer<JsonObject>> handlers) {
            this.subscriberId = subscriberId;
            this.schema = schema;
            this.table = table;
            this.events = new ArrayList<>(events);
            this.handlers = new HashMap<>(handlers);
        }
    }
    
    /**
     * Connection state enum.
     */
    private enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }
    
    /**
     * Statistics for a connection.
     */
    public static class ConnectionStats {
        public final String instanceName;
        public final String state;
        public final long connectedSince;
        public final int eventsReceived;
        public final int eventsFailed;
        public final int reconnections;
        public final int subscriberCount;
        
        ConnectionStats(String instanceName, String state, long connectedSince,
                       int eventsReceived, int eventsFailed, int reconnections,
                       int subscriberCount) {
            this.instanceName = instanceName;
            this.state = state;
            this.connectedSince = connectedSince;
            this.eventsReceived = eventsReceived;
            this.eventsFailed = eventsFailed;
            this.reconnections = reconnections;
            this.subscriberCount = subscriberCount;
        }
        
        @Override
        public String toString() {
            long uptime = connectedSince > 0 ? (System.currentTimeMillis() - connectedSince) / 1000 : 0;
            return String.format("Instance: %s, State: %s, Uptime: %ds, Events: %d/%d, Reconnects: %d, Subscribers: %d",
                               instanceName, state, uptime, eventsReceived, eventsFailed, reconnections, subscriberCount);
        }
    }
}
