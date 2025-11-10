package com.catchmind_be.game.response;

public record GameEventMessage(
    String type,
    int currentRound,
    int totalRound,
    String currentDrawerId,
    String word,
    boolean gameFinished
) {
}
