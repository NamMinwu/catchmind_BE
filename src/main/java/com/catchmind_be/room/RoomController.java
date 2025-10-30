package com.catchmind_be.room;

import com.catchmind_be.common.exception.response.ApiResponse;
import com.catchmind_be.room.entity.Room;
import com.catchmind_be.room.request.CreateRoomRequest;
import com.catchmind_be.room.request.JoinRoomRequest;
import com.catchmind_be.room.response.CreateRoomResponse;
import com.catchmind_be.room.response.JoinRoomResponse;
import com.catchmind_be.room.response.LeaveRoomResponse;
import lombok.AllArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
  private final SimpMessagingTemplate messagingTemplate;

  @PostMapping
  public ApiResponse<CreateRoomResponse> createRoom(@RequestBody CreateRoomRequest createRoomRequest) {
    Room room = roomService.createRoom(createRoomRequest.nickname());
    CreateRoomResponse createRoomResponse = CreateRoomResponse.from(room);
    return ApiResponse.success(createRoomResponse);
  }

  @GetMapping("/{roomCode}")
  public Room getRoom(@PathVariable String roomCode) {
    return roomService.getRoom(roomCode);
  }

  @PostMapping("/{roomCode}/players")
  public ApiResponse<JoinRoomResponse> joinRoom(@PathVariable String roomCode, @RequestBody JoinRoomRequest joinRoomRequest) {
    JoinRoomResponse joinRoomResponse = roomService.joinRoom(roomCode, joinRoomRequest.nickname());
    broadcastState(joinRoomResponse);
    return ApiResponse.success(joinRoomResponse);
  }

  @DeleteMapping("/{roomCode}/players/{playerId}")
  public ApiResponse<LeaveRoomResponse> leaveRoom(@PathVariable String roomCode, @PathVariable String playerId) {
    LeaveRoomResponse leaveRoomResponse = roomService.leaveRoom(roomCode, playerId);
    return ApiResponse.success(leaveRoomResponse);
  }

  private void broadcastState(JoinRoomResponse joinRoomResponse) {
    messagingTemplate.convertAndSend("/topic/rooms/" + joinRoomResponse.roomCode() + "/state",
        joinRoomResponse);
  }
}
