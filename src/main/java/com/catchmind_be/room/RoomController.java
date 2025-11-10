package com.catchmind_be.room;

import com.catchmind_be.common.exception.response.ApiResponse;
import com.catchmind_be.game.GameService;
import com.catchmind_be.game.response.GameEventMessage;
import com.catchmind_be.game.response.GameState;
import com.catchmind_be.room.entity.Room;
import com.catchmind_be.room.request.CreateRoomRequest;
import com.catchmind_be.room.request.JoinRoomRequest;
import com.catchmind_be.room.response.CreateRoomResponse;
import com.catchmind_be.room.response.RoomSnapshotResponse;
import com.catchmind_be.room.response.LeaveRoomResponse;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/api/rooms")
public class RoomController {
  private final RoomService roomService;
  private final GameService gameService;

  @PostMapping
  public ApiResponse<CreateRoomResponse> createRoom(@RequestBody CreateRoomRequest createRoomRequest) {
    Room room = roomService.createRoom(createRoomRequest.nickname());
    CreateRoomResponse createRoomResponse = CreateRoomResponse.from(room);
    return ApiResponse.success(createRoomResponse);
  }

  @GetMapping("/{roomCode}")
  public ApiResponse<RoomSnapshotResponse> getRoom(@PathVariable String roomCode) {
    return ApiResponse.success(roomService.getRoom(roomCode));
  }

  @PostMapping("/{roomCode}/players")
  public ApiResponse<RoomSnapshotResponse> joinRoom(@PathVariable String roomCode, @RequestBody JoinRoomRequest joinRoomRequest) {
    RoomSnapshotResponse roomSnapshotResponse = roomService.joinRoom(roomCode, joinRoomRequest.nickname());
    roomService.broadcastState(roomSnapshotResponse);
    return ApiResponse.success(roomSnapshotResponse);
  }

  @PostMapping("/{roomCode}/start")
  public ApiResponse<RoomSnapshotResponse> startGame(@PathVariable String roomCode){
    GameState gameState = gameService.startGame(roomCode);
    RoomSnapshotResponse roomSnapshotResponse = roomService.getRoom(roomCode);
    roomService.broadcastState(roomSnapshotResponse);
    roomService.broadcastGameEvent(roomCode, new GameEventMessage(
        "ROUND_STARTED",
        gameState.currentRound(),
        gameState.totalRounds(),
        gameState.currentDrawerId(),
        gameState.word(),
        false
    ));
    return ApiResponse.success(roomSnapshotResponse);
  }

  @DeleteMapping("/{roomCode}/players/{playerId}")
  public ApiResponse<LeaveRoomResponse> leaveRoom(@PathVariable String roomCode, @PathVariable String playerId) {
    LeaveRoomResponse leaveRoomResponse = roomService.leaveRoom(roomCode, playerId);
    return ApiResponse.success(leaveRoomResponse);
  }

}
