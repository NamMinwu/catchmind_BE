package com.catchmind_be.room.response;

import java.util.List;

public record RoomStateResponse(
    String roomCode,
    String hostPlayerId,
    int totalRounds,
    int currentRound,
    boolean started,
    List<PlayerSummary> players
) {
  public record PlayerSummary(String id, String nickname, int score, boolean host, boolean drawer) {}
}