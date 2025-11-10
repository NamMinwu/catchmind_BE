package com.catchmind_be.room.response;

import com.catchmind_be.player.response.PlayerResponse;
import java.util.List;

public record RoomSnapshotResponse(
    String roomCode,
    String hostPlayerId,
    List<PlayerResponse> players,
    String status,
    int totalRounds,
    int currentRound
) {
}

