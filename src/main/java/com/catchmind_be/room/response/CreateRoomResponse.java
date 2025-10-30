package com.catchmind_be.room.response;

import com.catchmind_be.room.entity.Room;

public record CreateRoomResponse(
    String code,
    String hostplayerId,
    Integer maxPlayers,
    Integer round,
    String status
) {
  public static CreateRoomResponse from(Room room) {
    return new CreateRoomResponse(
        room.getCode(),
        room.getHostPlayerId(),
        room.getMaxPlayers(),
        room.getRound(),
        room.getStatus().name()
    );
  }

}
