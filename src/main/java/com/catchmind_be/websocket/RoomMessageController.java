package com.catchmind_be.websocket;

import com.catchmind_be.common.utils.WordGenerator;
import com.catchmind_be.game.GameService;
import com.catchmind_be.game.entity.GameSession;
import com.catchmind_be.game.response.FinishedInfo;
import com.catchmind_be.game.response.GameEventMessage;
import com.catchmind_be.game.response.GuessResult;
import com.catchmind_be.room.RoomService;
import com.catchmind_be.room.response.RoomSnapshotResponse;
import com.catchmind_be.websocket.response.ChatMessage;
import com.catchmind_be.websocket.response.DrawMessage;
import lombok.AllArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;


@RestController
@AllArgsConstructor
public class RoomMessageController {

  private final GameService gameService;
  private final RoomService roomService;
  private final SimpMessagingTemplate template;

  @MessageMapping("/rooms/{roomCode}/chat")
  public void sendMessage(@PathVariable String roomCode, ChatMessage message) {
    this.template.convertAndSend("/topic/rooms/" + roomCode + "/chat", message);
    GuessResult guessResult = gameService.guessWord(
        roomCode, message.playerId(), message.message()
    );
    if (guessResult.correct()) {
      RoomSnapshotResponse snapshot = roomService.getRoom(roomCode);
      roomService.broadcastState(snapshot);
    }
  }

  @MessageMapping("/rooms/{roomCode}/draw")
  public void drawMessage(@PathVariable String roomCode, DrawMessage message) {
    if(!gameService.canDraw(roomCode, message)){
      return;
    }
    this.template.convertAndSend("/topic/rooms/" + roomCode + "/draw", message);
  }

}
