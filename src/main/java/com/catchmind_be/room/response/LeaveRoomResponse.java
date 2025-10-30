package com.catchmind_be.room.response;

public record LeaveRoomResponse(
    String roomCode,
    boolean roomDeleted,          // 방이 비어서 삭제됐는지
    String newHostPlayerId,       // 방장이 바뀌었으면 새 호스트 ID
    long remainingPlayers         // 남은 인원 수
) {
}
