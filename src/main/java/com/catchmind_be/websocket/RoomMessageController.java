package com.catchmind_be.websocket;

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

  private final SimpMessagingTemplate template;

  @MessageMapping("/rooms/{roomCode}/chat")
  public void sendMessage(@PathVariable String roomCode, ChatMessage message) {
    // 게임이 시작하고 중간에 정답을 찾게 된다면 ㅇㅋ
    this.template.convertAndSend("/topic/rooms/" + roomCode + "/chat", message);
  }

  @MessageMapping("/rooms/{roomCode}/draw")
  public void drawMessage(@PathVariable String roomCode, DrawMessage message) {
    this.template.convertAndSend("/topic/rooms/" + roomCode + "/draw", message);
  }

}
