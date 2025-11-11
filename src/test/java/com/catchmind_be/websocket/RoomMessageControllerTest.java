package com.catchmind_be.websocket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.catchmind_be.game.GameService;
import com.catchmind_be.game.entity.GameSession;
import com.catchmind_be.game.response.GuessResult;
import com.catchmind_be.player.response.PlayerResponse;
import com.catchmind_be.room.RoomService;
import com.catchmind_be.room.response.RoomSnapshotResponse;
import com.catchmind_be.websocket.response.ChatMessage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class RoomMessageControllerTest {

  @Mock
  private GameService gameService;
  @Mock
  private RoomService roomService;
  @Mock
  private SimpMessagingTemplate template;

  @InjectMocks
  private RoomMessageController controller;

  @Test
  void 메시지전송_채팅브로드캐스트와정답검증호출() {
    String roomCode = "ROOM1";
    ChatMessage chatMessage = new ChatMessage("1", "tester", "hello");
    when(gameService.guessWord(roomCode, chatMessage.playerId(), chatMessage.message()))
        .thenReturn(GuessResult.inCorrect());

    controller.sendMessage(roomCode, chatMessage);

    verify(template).convertAndSend("/topic/rooms/" + roomCode + "/chat", chatMessage);
    verify(gameService).guessWord(roomCode, chatMessage.playerId(), chatMessage.message());
  }

  @Test
  void 그림그리는플레이어는정답처리안됨() {
    String roomCode = "ROOM2";
    ChatMessage chatMessage = new ChatMessage("DRAWER", "drawer", "secret-word");
    when(gameService.guessWord(roomCode, chatMessage.playerId(), chatMessage.message()))
        .thenReturn(GuessResult.inCorrect());

    controller.sendMessage(roomCode, chatMessage);

    verify(template).convertAndSend("/topic/rooms/" + roomCode + "/chat", chatMessage);
    verify(gameService).guessWord(roomCode, chatMessage.playerId(), chatMessage.message());
    verify(roomService, never()).getRoom(any());
    verify(roomService, never()).broadcastState(any());
  }

  @Test
  void 오답입력시_방상태브로드캐스트없음() {
    String roomCode = "ROOM2";
    ChatMessage chatMessage = new ChatMessage("2", "tester", "wrong");
    when(gameService.guessWord(roomCode, chatMessage.playerId(), chatMessage.message()))
        .thenReturn(GuessResult.inCorrect());

    controller.sendMessage(roomCode, chatMessage);

    verify(roomService, never()).getRoom(any());
    verify(roomService, never()).broadcastState(any());
  }

  @Test
  void 정답맞춘경우_방상태브로드캐스트() {
    String roomCode = "ROOM3";
    ChatMessage chatMessage = new ChatMessage("3", "tester", "answer");
    GameSession gameSession = GameSession.create(1L, roomCode, 2, 60);
    GuessResult correctResult = new GuessResult(true, gameSession);
    RoomSnapshotResponse snapshotResponse = new RoomSnapshotResponse(
        roomCode,
        "host",
        List.<PlayerResponse>of(),
        "PLAYING",
        2,
        1
    );

    when(gameService.guessWord(roomCode, chatMessage.playerId(), chatMessage.message()))
        .thenReturn(correctResult);
    when(roomService.getRoom(roomCode)).thenReturn(snapshotResponse);

    controller.sendMessage(roomCode, chatMessage);

    verify(template).convertAndSend("/topic/rooms/" + roomCode + "/chat", chatMessage);
    verify(gameService).guessWord(roomCode, chatMessage.playerId(), chatMessage.message());
    verify(roomService).getRoom(roomCode);
    verify(roomService).broadcastState(snapshotResponse);
  }


}
