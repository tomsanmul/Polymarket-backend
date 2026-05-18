package com.polymarket.polymarket_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.polymarket_backend.dto.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = extractUserId(session);
        if (userId != null) {
            WebSocketSession existing = userSessions.put(userId, session);
            if (existing != null && existing.isOpen()) {
                try {
                    existing.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            log.info("WebSocket connected: userId={}, sessionId={}", userId, session.getId());
        } else {
            log.warn("WebSocket connected without userId, sessionId={}", session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = extractUserId(session);
        if (userId != null) {
            userSessions.remove(userId, session);
            log.info("WebSocket disconnected: userId={}, sessionId={}", userId, session.getId());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    }

    public void sendToUser(Long userId, NotificationMessage notification) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(notification);
                session.sendMessage(new TextMessage(json));
                log.debug("Notification sent to userId={}: {}", userId, notification.getTitle());
            } catch (IOException e) {
                log.warn("Failed to send notification to userId={}: {}", userId, e.getMessage());
                userSessions.remove(userId, session);
            }
        }
    }

    public void broadcast(NotificationMessage notification) {
        List<Long> disconnected = new java.util.ArrayList<>();
        for (var entry : userSessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            if (session.isOpen()) {
                try {
                    String json = objectMapper.writeValueAsString(notification);
                    session.sendMessage(new TextMessage(json));
                } catch (IOException e) {
                    disconnected.add(entry.getKey());
                }
            } else {
                disconnected.add(entry.getKey());
            }
        }
        disconnected.forEach(userSessions::remove);
    }

    public boolean isConnected(Long userId) {
        WebSocketSession session = userSessions.get(userId);
        return session != null && session.isOpen();
    }

    private Long extractUserId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri != null) {
            String query = uri.getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] parts = param.split("=", 2);
                    if (parts.length == 2 && "userId".equals(parts[0])) {
                        try {
                            return Long.parseLong(parts[1]);
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    }
                }
            }
        }
        return null;
    }
}
