package com.catchmind_be.game;

import com.catchmind_be.game.response.FinishedInfo;
import com.catchmind_be.game.response.GameState;
import com.catchmind_be.game.response.GuessResult;
import com.catchmind_be.player.PlayerRepository;
import com.catchmind_be.room.response.RoomSnapshotResponse;
import com.catchmind_be.websocket.response.DrawMessage;
import java.util.Arrays;
import java.util.Optional;
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

  private static final int SCORE_PER_SUCCESS = 100;
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
      RoomSnapshotResponse roomSnapshotResponse = buildRoomSnapshotResponse(gameSession.getRoomCode());
      messagingTemplate.convertAndSend("/topic/rooms/" + roomSnapshotResponse.roomCode() + "/state",
          roomSnapshotResponse);

      FinishedInfo finishedInfo = getFinishedInfo(gameSession);

      broadcastGameEvent(gameSession.getRoomCode(), new GameEventMessage(
          "ROUND_TIMEOUT",
          gameSession.getCurrentRound(),
          gameSession.getTotalRounds(),
          gameSession.getCurrentDrawerId(),
          gameSession.getWord(),
          finishedInfo.isFinished()
      ));

      if(!finishedInfo.isFinished()){
        nextRound(gameSession, finishedInfo);
        scheduleRoundTimeout(gameSession.getRoomId(), gameSession.getSecondsPerRound());
      }

      gameSession.markCompleted();
      cleanupAfterGame(roomId);
    });
  }


  private void nextRound(GameSession gameSession, FinishedInfo finishedInfo) {
    String newWord = wordGenerator.randomWord();
    gameSession.nextRound(newWord, finishedInfo.orderList().get(finishedInfo.nextIndex()));
    broadcastGameEvent(gameSession.getRoomCode(), new GameEventMessage(
        "ROUND_STARTED",
        gameSession.getCurrentRound(),
        gameSession.getTotalRounds(),
        gameSession.getCurrentDrawerId(),
        newWord,
        false
    ));
  }

  public FinishedInfo getFinishedInfo(GameSession gameSession) {
    List<String> orderList = getOrderList(gameSession.getDrawerOrder());
    int nextIndex = gameSession.getCurrentOrderIndex() + 1;
    boolean gameFinished = isFinished(gameSession, nextIndex, orderList);
    return new FinishedInfo(
        orderList,
        nextIndex,
        gameFinished
    );
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

  public GuessResult guessWord(String roomCode, String playerId ,String word) {
    Room room = roomRepository.findByCode(roomCode)
        .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

    if(word == null || word.isEmpty()) {
      return GuessResult.inCorrect();
    }

    if(room.getStatus() == RoomStatus.WAITING) {
      return GuessResult.inCorrect();
    }

    GameSession gameSession =gameSessionRepository.getOrCreate(room);

    // 그리는 사람이면 안되게 해야해
    if(isDrawer(gameSession, playerId)) {
      return GuessResult.inCorrect();
    }

    String currentWord = gameSession.getWord();
    String normalizedWord = word.trim();

    if(!currentWord.equals(normalizedWord)) {
      return GuessResult.inCorrect();
    }

    Player player = playerRepository.findById(Long.parseLong(playerId)).orElseThrow(
        () -> new CustomException(ErrorCode.PLAYER_NOT_FOUND)
    );
    player.setScore(player.getScore() + SCORE_PER_SUCCESS);
    playerRepository.save(player);

    return new GuessResult(true, gameSession);
  }

  @Transactional(readOnly = true)
  public boolean canDraw(String roomCode, DrawMessage drawMessage) {
    Room room = roomRepository.findByCode(roomCode)
        .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
    GameSession gameSession = gameSessionRepository.getOrCreate(room);
    String drawerId = drawMessage.playerId();
    return room.getStatus() == RoomStatus.PLAYING && drawerId.equals(gameSession.getCurrentDrawerId());
  }

  private boolean isDrawer(GameSession gameSession, String playerId) {
    return gameSession.getCurrentDrawerId().equals(playerId);
  }

  public boolean isFinished(GameSession gameSession, int nextIndex, List<String> orderList) {
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

  public void broadcastGameEvent(String roomCode, GameEventMessage startEvent){
    messagingTemplate.convertAndSend("/topic/rooms/" + roomCode + "/game", startEvent);
  }
}
