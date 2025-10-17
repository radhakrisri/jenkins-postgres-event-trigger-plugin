package io.jenkins.plugins.supabase;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import hudson.util.Secret;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SupabaseRealtimeClient extends WebSocketClient {
    
    private static final Logger LOGGER = Logger.getLogger(SupabaseRealtimeClient.class.getName());
    private static final Gson GSON = new Gson();
    
    private final String apiKey;
    private final Map<String, Consumer<JsonObject>> eventHandlers = new ConcurrentHashMap<>();
    private final AtomicInteger refCounter = new AtomicInteger(0);
    private final Map<String, String> channelRefs = new ConcurrentHashMap<>();
    private String accessToken;
    
    // Callbacks for connection lifecycle
    private Runnable onOpenCallback;
    private java.util.function.Consumer<String> onCloseCallback; // Consumer<reason>
    private java.util.function.Consumer<Exception> onErrorCallback;

    public SupabaseRealtimeClient(String url, Secret apiKey) throws Exception {
        super(new URI(url));
        this.apiKey = apiKey != null ? apiKey.getPlainText() : null;
        // Only add header if apiKey is provided (for backward compatibility)
        if (this.apiKey != null) {
            addHeader("apikey", this.apiKey);
        }
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        LOGGER.info("Connected to Supabase Realtime");
        if (onOpenCallback != null) {
            onOpenCallback.run();
        } else {
            // Legacy behavior: send heartbeat if no callback set
            sendHeartbeat();
        }
    }

    @Override
    public void onMessage(String message) {
        try {
            LOGGER.info("Received message: " + message);
            JsonObject json = GSON.fromJson(message, JsonObject.class);
            String event = json.has("event") ? json.get("event").getAsString() : null;
            String topic = json.has("topic") ? json.get("topic").getAsString() : null;
            
            LOGGER.info("Parsed message - Event: " + event + ", Topic: " + topic);
            
            if (event != null && topic != null) {
                // Handle postgres_changes events
                if ("postgres_changes".equals(event)) {
                    JsonElement payloadElement = json.get("payload");
                    if (payloadElement != null && payloadElement.isJsonObject()) {
                        JsonObject payload = payloadElement.getAsJsonObject();
                        JsonElement dataElement = payload.get("data");
                        if (dataElement != null && dataElement.isJsonObject()) {
                            JsonObject data = dataElement.getAsJsonObject();
                            String type = data.has("type") ? data.get("type").getAsString() : null;
                            if (type != null) {
                                // Look up handler for this operation type
                                String handlerKey = topic + ":" + type;
                                Consumer<JsonObject> handler = eventHandlers.get(handlerKey);
                                if (handler != null) {
                                    LOGGER.info("Invoking handler for " + type + " event on " + topic);
                                    handler.accept(data);
                                } else {
                                    LOGGER.fine("No handler registered for key: " + handlerKey);
                                }
                            }
                        }
                    }
                } else {
                    // Handle other event types normally
                    String key = topic + ":" + event;
                    Consumer<JsonObject> handler = eventHandlers.get(key);
                    if (handler != null) {
                        JsonElement payload = json.get("payload");
                        if (payload != null && payload.isJsonObject()) {
                            handler.accept(payload.getAsJsonObject());
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error processing message: " + message, e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        LOGGER.info("Disconnected from Supabase Realtime: " + reason);
        if (onCloseCallback != null) {
            onCloseCallback.accept(code + ":" + reason + ":" + remote);
        }
    }

    @Override
    public void onError(Exception ex) {
        LOGGER.log(Level.SEVERE, "WebSocket error", ex);
        if (onErrorCallback != null) {
            onErrorCallback.accept(ex);
        }
    }

    public void subscribeToTable(String schema, String table, String event, Consumer<JsonObject> handler) {
        String topic = "realtime:" + schema + ":" + table;
        String ref = String.valueOf(refCounter.incrementAndGet());
        
        // Store channel reference
        channelRefs.put(topic, ref);
        
        // Register handler
        String handlerKey = topic + ":" + event;
        eventHandlers.put(handlerKey, handler);
        
        // Send join message
        JsonObject message = new JsonObject();
        message.addProperty("topic", topic);
        message.addProperty("event", "phx_join");
        message.addProperty("ref", ref);
        
        JsonObject payload = new JsonObject();
        JsonObject config = new JsonObject();
        payload.add("config", config);
        message.add("payload", payload);
        
        send(GSON.toJson(message));
        LOGGER.info("Subscribed to " + topic + " for event " + event);
    }

    /**
     * Subscribe to multiple events on a table at once.
     * This is the recommended approach as it joins the channel once with all postgres_changes configured.
     */
    public void subscribeToTableEvents(String schema, String table, List<String> events, 
                                       Map<String, Consumer<JsonObject>> eventHandlers) {
        String topic = "realtime:" + schema + ":" + table;
        String ref = String.valueOf(refCounter.incrementAndGet());
        
        // Store channel reference
        channelRefs.put(topic, ref);
        
        // Register all handlers
        for (Map.Entry<String, Consumer<JsonObject>> entry : eventHandlers.entrySet()) {
            String handlerKey = topic + ":" + entry.getKey();
            this.eventHandlers.put(handlerKey, entry.getValue());
        }
        
        // Build postgres_changes array
        JsonArray postgresChanges = new JsonArray();
        for (String event : events) {
            JsonObject change = new JsonObject();
            change.addProperty("event", event);
            change.addProperty("schema", schema);
            change.addProperty("table", table);
            postgresChanges.add(change);
        }
        
        // Send join message with config
        JsonObject message = new JsonObject();
        message.addProperty("topic", topic);
        message.addProperty("event", "phx_join");
        message.addProperty("ref", ref);
        
        JsonObject payload = new JsonObject();
        JsonObject config = new JsonObject();
        config.add("postgres_changes", postgresChanges);
        payload.add("config", config);
        message.add("payload", payload);
        
        send(GSON.toJson(message));
        LOGGER.info("Subscribed to " + topic + " for events: " + events);
    }

    public void unsubscribeFromTable(String schema, String table) {
        String topic = "realtime:" + schema + ":" + table;
        String ref = channelRefs.remove(topic);
        
        if (ref != null) {
            JsonObject message = new JsonObject();
            message.addProperty("topic", topic);
            message.addProperty("event", "phx_leave");
            message.addProperty("ref", ref);
            message.add("payload", new JsonObject());
            
            send(GSON.toJson(message));
            
            // Remove handlers
            eventHandlers.keySet().removeIf(key -> key.startsWith(topic + ":"));
            LOGGER.info("Unsubscribed from " + topic);
        }
    }
    
    /**
     * Set callback to be invoked when connection opens.
     */
    public void setOnOpenCallback(Runnable callback) {
        this.onOpenCallback = callback;
    }
    
    /**
     * Set callback to be invoked when connection closes.
     * Callback receives a string in format "code:reason:remote".
     */
    public void setOnCloseCallback(java.util.function.Consumer<String> callback) {
        this.onCloseCallback = callback;
    }
    
    /**
     * Set callback to be invoked when connection error occurs.
     */
    public void setOnErrorCallback(java.util.function.Consumer<Exception> callback) {
        this.onErrorCallback = callback;
    }

    public void sendHeartbeat() {
        if (isOpen()) {
            JsonObject message = new JsonObject();
            message.addProperty("topic", "phoenix");
            message.addProperty("event", "heartbeat");
            message.addProperty("ref", String.valueOf(refCounter.incrementAndGet()));
            message.add("payload", new JsonObject());
            
            send(GSON.toJson(message));
            
            // Schedule next heartbeat
            new Thread(() -> {
                try {
                    Thread.sleep(30000); // 30 seconds
                    sendHeartbeat();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }
}
