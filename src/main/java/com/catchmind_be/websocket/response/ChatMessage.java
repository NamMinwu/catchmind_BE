package com.catchmind_be.websocket.response;

public record ChatMessage(String playerId, String nickname, String message) {
}
