package com.exchange.web;

import com.exchange.service.MarketDataBroadcaster;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Pushes live market data (order-book snapshots and trades) to every connected
 * client. Implements {@link MarketDataBroadcaster} so the exchange can publish
 * without knowing anything about WebSockets. Messages are JSON envelopes of the
 * form {@code {"topic":"book"|"trade","data":{...}}}.
 */
@Component
public class MarketDataWebSocketHandler extends TextWebSocketHandler implements MarketDataBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(MarketDataWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    public void broadcast(String topic, Object payload) {
        if (sessions.isEmpty()) return;
        String json;
        try {
            json = mapper.writeValueAsString(Map.of("topic", topic, "data", payload));
        } catch (Exception e) {
            log.warn("failed to serialise market data", e);
            return;
        }
        TextMessage msg = new TextMessage(json);
        for (WebSocketSession s : sessions) {
            try {
                if (s.isOpen()) synchronized (s) { s.sendMessage(msg); }
            } catch (Exception e) {
                sessions.remove(s);
            }
        }
    }
}
