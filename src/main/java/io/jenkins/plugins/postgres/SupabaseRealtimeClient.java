package io.jenkins.plugins.postgres;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import hudson.util.Secret;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SupabaseRealtimeClient extends WebSocketClient {
    
    private static final Logger LOGGER = Logger.getLogger(SupabaseRealtimeClient.class.getName());
    private static final Gson GSON = new Gson();
    
    private final String apiKey;
    private final Map<String, Consumer<JsonObject>> eventHandlers = new HashMap<>();
    private final AtomicInteger refCounter = new AtomicInteger(0);
    private final Map<String, String> channelRefs = new HashMap<>();
    private String accessToken;

    public SupabaseRealtimeClient(String url, Secret apiKey) throws Exception {
        super(new URI(url));
        this.apiKey = apiKey != null ? apiKey.getPlainText() : null;
        addHeader("apikey", this.apiKey);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        LOGGER.info("Connected to Supabase Realtime");
        sendHeartbeat();
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonObject json = GSON.fromJson(message, JsonObject.class);
            String event = json.has("event") ? json.get("event").getAsString() : null;
            String topic = json.has("topic") ? json.get("topic").getAsString() : null;
            
            LOGGER.fine("Received message - Event: " + event + ", Topic: " + topic);
            
            if (event != null && topic != null) {
                String key = topic + ":" + event;
                Consumer<JsonObject> handler = eventHandlers.get(key);
                if (handler != null) {
                    JsonElement payload = json.get("payload");
                    if (payload != null && payload.isJsonObject()) {
                        handler.accept(payload.getAsJsonObject());
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
    }

    @Override
    public void onError(Exception ex) {
        LOGGER.log(Level.SEVERE, "WebSocket error", ex);
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
        payload.addProperty("config", "{}");
        message.add("payload", payload);
        
        send(GSON.toJson(message));
        LOGGER.info("Subscribed to " + topic + " for event " + event);
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

    private void sendHeartbeat() {
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
