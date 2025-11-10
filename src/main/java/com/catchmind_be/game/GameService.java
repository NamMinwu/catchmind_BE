package com.catchmind_be.game;

import com.catchmind_be.game.response.GameState;
import com.catchmind_be.player.PlayerRepository;
import com.catchmind_be.room.response.RoomSnapshotResponse;
import java.util.Arrays;
import java.util.stream.Collectors;
import com.catchmind_be.common.exception.CustomException;
import com.catchmind_be.common.exception.code.ErrorCode;
import com.catchmind_be.common.utils.WordGenerator;
import com.catchmind_be.game.entity.GameStatus;
import com.catchmind_be.game.entity.GameSession;
import com.catchmind_be.game.response.GameEventMessage;
import com.catchmind_be.player.entity.Player;
import com.catchmind_be.player.response.PlayerResponse;
import com.catchmind_be.room.RoomRepository;
import com.catchmind_be.room.entity.Room;
import com.catchmind_be.room.entity.RoomStatus;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@AllArgsConstructor
public class GameService {

  private static final int DEFAULT_ROUND_DURATION_SECONDS = 60;

  private final RoomRepository roomRepository;
  private final PlayerRepository playerRepository;
  private final GameSessionRepository gameSessionRepository;
  private final GameScheduler gameScheduler;
  private final TransactionTemplate transactionTemplate;
  private final SimpMessagingTemplate messagingTemplate;
  private final WordGenerator wordGenerator;

  @Transactional
  public GameState startGame(String roomCode) {
    Room room = roomRepository.findByCode(roomCode)
        .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

    List<Player> players = playerRepository.findPlayersByRoomCodeOrdered(room.getCode());
    int totalRounds = players.size();

    if(totalRounds < 2){
      throw new CustomException(ErrorCode.NOT_ENOUGH_PLAYER);
    }

    if (RoomStatus.PLAYING.equals(room.getStatus())) {
      throw new CustomException(ErrorCode.GAME_ALREADY_STARTED);
    }

    int duration = DEFAULT_ROUND_DURATION_SECONDS;
    cancelScheduledTask(room.getId());

    GameSession gameSession = gameSessionRepository.create(room.getId(), roomCode,totalRounds, duration);
    gameSession.start(wordGenerator.randomWord(), players.getFirst().getId().toString(), joinOrder(players));

    room.setStatus(RoomStatus.PLAYING);
    roomRepository.save(room);

    scheduleRoundTimeout(room.getId(), duration);

    return GameState.toGameState(gameSession);
  }


  public GameSession getOrGreateGameSession(Long roomId) {
    Room room = roomRepository.findById(roomId)
        .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
    return gameSessionRepository.getOrCreate(room);
  }

  private void scheduleRoundTimeout(Long roomId, int durationSeconds) {
    gameScheduler.schedule(roomId, () -> executeRoundTimeout(roomId),
        Instant.now().plusSeconds(durationSeconds));
  }


  @Transactional(readOnly = true)
  public void executeRoundTimeout(Long roomId) {
    transactionTemplate.executeWithoutResult(status -> {
      GameSession gameSession = getOrGreateGameSession(roomId);
      // 진행중 아니면 스케줄러에서 삭제
      if (!GameStatus.IN_PROGRESS.equals(gameSession.getStatus())) {
        gameScheduler.cancel(roomId);
        return;
      }
      // 리펙 필요
      RoomSnapshotResponse roomSnapshotResponse = buildRoomSnapshotResponse(gameSession.getRoomCode());

      messagingTemplate.convertAndSend("/topic/rooms/" + roomSnapshotResponse.roomCode() + "/state",
          roomSnapshotResponse);

      List<String> orderList = getOrderList(gameSession.getDrawerOrder());
      int nextIndex = gameSession.getCurrentOrderIndex() + 1;
      boolean gameFinished = isFinished(gameSession, nextIndex, orderList);


      GameEventMessage timeoutEvent = new GameEventMessage(
          "ROUND_TIMEOUT",
          gameSession.getCurrentRound(),
          gameSession.getTotalRounds(),
          gameSession.getCurrentPlayerId(),
          gameSession.getWord(),
          gameFinished
      );

      messagingTemplate.convertAndSend("/topic/rooms/" + gameSession.getRoomCode() + "/game", timeoutEvent);

      if(!gameFinished){
        String newWord = wordGenerator.randomWord();
        gameSession.nextRound(newWord, orderList.get(nextIndex));
        GameEventMessage startEvent = new GameEventMessage(
            "ROUND_STARTED",
            gameSession.getCurrentRound(),
            gameSession.getTotalRounds(),
            gameSession.getCurrentPlayerId(),
            newWord,
            false
        );
        messagingTemplate.convertAndSend("/topic/rooms/" + gameSession.getRoomCode() + "/game", startEvent);
        scheduleRoundTimeout(roomId, gameSession.getSecondsPerRound());
        return;
      }

      gameSession.markCompleted();
      cleanupAfterGame(roomId);
    });
  }

  @Transactional
  public void endGame(Long roomId) {
    GameSession gameSession = getOrGreateGameSession(roomId);
    if (GameStatus.IN_PROGRESS.equals(gameSession.getStatus())) {
      gameSession.markCompleted();
    } else {
      gameSession.resetToIdle();
    }
    cleanupAfterGame(roomId);
  }

  private boolean isFinished(GameSession gameSession, int nextIndex, List<String> orderList) {
    return nextIndex >= orderList.size() || nextIndex >= gameSession.getTotalRounds();
  }

  private String joinOrder(List<Player> players) {
    return players.stream()
        .map(player -> String.valueOf(player.getId()))
        .collect(Collectors.joining(","));
  }

  private List<String> getOrderList(String drawerOrder) {
    if (drawerOrder == null || drawerOrder.isBlank()) {
      return List.of();
    }
    return Arrays.stream(drawerOrder.split(","))
        .filter(token -> !token.isBlank())
        .toList();
  }


  private void cancelScheduledTask(Long roomId) {
    gameScheduler.cancel(roomId);
  }

  private void cleanupAfterGame(Long roomId) {
    roomRepository.findById(roomId).ifPresent(room -> {
      room.setStatus(RoomStatus.WAITING);
      roomRepository.save(room);
    });
    gameSessionRepository.remove(roomId);
    gameScheduler.cancel(roomId);
  }


  private RoomSnapshotResponse buildRoomSnapshotResponse(String roomCode) {
    Room room = roomRepository.findByCode(roomCode)
        .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

    List<PlayerResponse> players = playerRepository.findPlayersByRoomCodeOrdered(room.getCode())
        .stream()
        .map(PlayerResponse::from)
        .toList();

    GameSession gameSession = gameSessionRepository.getOrCreate(room);

    return new RoomSnapshotResponse(
        room.getCode(),
        room.getHostPlayerId(),
        players,
        room.getStatus().name(),
        gameSession.getTotalRounds(),
        gameSession.getCurrentRound()
    );
  }
}
