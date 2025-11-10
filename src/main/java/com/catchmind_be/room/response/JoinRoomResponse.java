package com.catchmind_be.room.response;

import com.catchmind_be.player.response.PlayerResponse;
import java.util.List;

public record JoinRoomResponse(
    String roomCode,
    String hostPlayerId,
    List<PlayerResponse> players,
    String status
) {
}

