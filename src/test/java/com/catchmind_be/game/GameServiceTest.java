package com.catchmind_be.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.catchmind_be.common.exception.CustomException;
import com.catchmind_be.common.exception.code.ErrorCode;
import com.catchmind_be.common.utils.WordGenerator;
import com.catchmind_be.game.entity.GameSession;
import com.catchmind_be.game.entity.GameStatus;
import com.catchmind_be.game.response.GameEventMessage;
import com.catchmind_be.game.response.GameState;
import com.catchmind_be.player.PlayerRepository;
import com.catchmind_be.player.entity.Player;
import com.catchmind_be.room.RoomRepository;
import com.catchmind_be.room.entity.Room;
import com.catchmind_be.room.entity.RoomStatus;
import com.catchmind_be.room.response.RoomSnapshotResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

  @Mock
  private RoomRepository roomRepository;
  @Mock
  private PlayerRepository playerRepository;
  @Mock
  private GameSessionRepository gameSessionRepository;
  @Mock
  private GameScheduler gameScheduler;
  @Mock
  private TransactionTemplate transactionTemplate;
  @Mock
  private SimpMessagingTemplate messagingTemplate;
  @Mock
  private WordGenerator wordGenerator;

  @InjectMocks
  private GameService gameService;

  @Test
  void 게임시작_라운드수와스케줄확인() {
    Room room = Room.builder()
        .id(1L)
        .code("ROOM01")
        .status(RoomStatus.WAITING)
        .createdAt(Instant.now())
        .build();

    Player host = 플레이어생성(10L, room, "host");
    Player guest = 플레이어생성(11L, room, "guest");
    List<Player> players = List.of(host, guest);

    when(roomRepository.findByCode(room.getCode())).thenReturn(Optional.of(room));
    when(playerRepository.findPlayersByRoomCodeOrdered(room.getCode())).thenReturn(players);

    GameSession session = GameSession.create(room.getId(), room.getCode(), players.size(), 60);
    when(gameSessionRepository.create(room.getId(), room.getCode(), players.size(), 60)).thenReturn(session);
    when(wordGenerator.randomWord()).thenReturn("test-word");

    Instant beforeStart = Instant.now();

    GameState result = gameService.startGame(room.getCode());

    assertThat(result.totalRounds()).isEqualTo(players.size());
    assertThat(result.currentRound()).isEqualTo(1);
    assertThat(result.currentDrawerId()).isEqualTo(String.valueOf(host.getId()));
    assertThat(result.word()).isEqualTo("test-word");
    assertThat(room.getStatus()).isEqualTo(RoomStatus.PLAYING);

    ArgumentCaptor<Instant> startAtCaptor = ArgumentCaptor.forClass(Instant.class);
    verify(gameScheduler).cancel(room.getId());
    verify(gameScheduler).schedule(eq(room.getId()), any(Runnable.class), startAtCaptor.capture());

    long deltaMillis = Math.abs(Duration.between(beforeStart.plusSeconds(60), startAtCaptor.getValue()).toMillis());
    assertThat(deltaMillis).isLessThan(1000L);
  }

  @Test
  void 게임시작_인원부족시예외() {
    Room room = Room.builder()
        .id(3L)
        .code("ROOM03")
        .status(RoomStatus.WAITING)
        .createdAt(Instant.now())
        .build();

    Player onlyPlayer = 플레이어생성(31L, room, "solo");

    when(roomRepository.findByCode(room.getCode())).thenReturn(Optional.of(room));
    when(playerRepository.findPlayersByRoomCodeOrdered(room.getCode())).thenReturn(List.of(onlyPlayer));

    assertThatThrownBy(() -> gameService.startGame(room.getCode()))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_ENOUGH_PLAYER);

    verify(gameSessionRepository, never()).create(anyLong(), anyString(), anyInt(), anyInt());
    verify(gameScheduler, never()).schedule(any(), any(), any());
  }

  @Test
  void 게임시작_이미진행중이면예외() {
    Room room = Room.builder()
        .id(4L)
        .code("ROOM04")
        .status(RoomStatus.PLAYING)
        .createdAt(Instant.now())
        .build();

    Player host = 플레이어생성(41L, room, "host");
    Player guest = 플레이어생성(42L, room, "guest");

    when(roomRepository.findByCode(room.getCode())).thenReturn(Optional.of(room));
    when(playerRepository.findPlayersByRoomCodeOrdered(room.getCode())).thenReturn(List.of(host, guest));

    assertThatThrownBy(() -> gameService.startGame(room.getCode()))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GAME_ALREADY_STARTED);

    verify(gameSessionRepository, never()).create(anyLong(), anyString(), anyInt(), anyInt());
  }

  @Test
  void 게임시작_기존스케줄취소후등록() {
    Room room = Room.builder()
        .id(5L)
        .code("ROOM05")
        .status(RoomStatus.WAITING)
        .createdAt(Instant.now())
        .build();

    Player host = 플레이어생성(51L, room, "host");
    Player guest = 플레이어생성(52L, room, "guest");
    List<Player> players = List.of(host, guest);

    when(roomRepository.findByCode(room.getCode())).thenReturn(Optional.of(room));
    when(playerRepository.findPlayersByRoomCodeOrdered(room.getCode())).thenReturn(players);
    when(gameSessionRepository.create(room.getId(), room.getCode(), players.size(), 60))
        .thenReturn(GameSession.create(room.getId(), room.getCode(), players.size(), 60));
    when(wordGenerator.randomWord()).thenReturn("word");

    gameService.startGame(room.getCode());

    InOrder inOrder = inOrder(gameScheduler);
    inOrder.verify(gameScheduler).cancel(room.getId());
    inOrder.verify(gameScheduler).schedule(eq(room.getId()), any(Runnable.class), any(Instant.class));
  }

  @Test
  void 라운드타임아웃_마지막라운드후대기상태() {
    Room room = Room.builder()
        .id(2L)
        .code("ROOM02")
        .status(RoomStatus.PLAYING)
        .createdAt(Instant.now())
        .build();

    Player host = 플레이어생성(21L, room, "host");
    Player guest = 플레이어생성(22L, room, "guest");
    List<Player> players = List.of(host, guest);

    GameSession session = GameSession.create(room.getId(), room.getCode(), players.size(), 60);
    String order = host.getId() + "," + guest.getId();
    session.start("word-1", host.getId().toString(), order);
    session.nextRound("word-2", guest.getId().toString());

    when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
    when(roomRepository.findByCode(room.getCode())).thenReturn(Optional.of(room));
    when(playerRepository.findPlayersByRoomCodeOrdered(room.getCode())).thenReturn(players);
    when(gameSessionRepository.getOrCreate(room)).thenReturn(session);

    트랜잭션템플릿스텁();

    gameService.executeRoundTimeout(room.getId());

    assertThat(room.getStatus()).isEqualTo(RoomStatus.WAITING);
    verify(gameSessionRepository).remove(room.getId());
    verify(gameScheduler).cancel(room.getId());
  }

  @Test
  void 라운드타임아웃_진행중아니면취소() {
    Room room = Room.builder()
        .id(6L)
        .code("ROOM06")
        .status(RoomStatus.WAITING)
        .createdAt(Instant.now())
        .build();

    GameSession session = GameSession.create(room.getId(), room.getCode(), 0, 60);

    when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
    when(gameSessionRepository.getOrCreate(room)).thenReturn(session);

    트랜잭션템플릿스텁();

    gameService.executeRoundTimeout(room.getId());

    verify(gameScheduler).cancel(room.getId());
    verifyNoInteractions(messagingTemplate);
    verify(gameSessionRepository, never()).remove(room.getId());
  }

  @Test
  void 라운드타임아웃_다음플레이어로진행() {
    Room room = Room.builder()
        .id(7L)
        .code("ROOM07")
        .status(RoomStatus.PLAYING)
        .createdAt(Instant.now())
        .hostPlayerId("71")
        .build();

    Player p1 = 플레이어생성(71L, room, "p1");
    Player p2 = 플레이어생성(72L, room, "p2");
    Player p3 = 플레이어생성(73L, room, "p3");
    List<Player> players = List.of(p1, p2, p3);

    GameSession session = GameSession.create(room.getId(), room.getCode(), players.size(), 60);
    String order = p1.getId() + "," + p2.getId() + "," + p3.getId();
    session.start("word-1", p1.getId().toString(), order);

    when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
    when(roomRepository.findByCode(room.getCode())).thenReturn(Optional.of(room));
    when(playerRepository.findPlayersByRoomCodeOrdered(room.getCode())).thenReturn(players);
    when(gameSessionRepository.getOrCreate(room)).thenReturn(session);
    when(wordGenerator.randomWord()).thenReturn("word-2");

    트랜잭션템플릿스텁();

    gameService.executeRoundTimeout(room.getId());

    assertThat(session.getCurrentRound()).isEqualTo(2);
    assertThat(session.getCurrentPlayerId()).isEqualTo(String.valueOf(p2.getId()));

    ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(messagingTemplate, times(3)).convertAndSend(destinationCaptor.capture(), payloadCaptor.capture());

    List<String> destinations = destinationCaptor.getAllValues();
    assertThat(destinations.get(0)).isEqualTo("/topic/rooms/" + room.getCode() + "/state");
    assertThat(destinations.get(1)).isEqualTo("/topic/rooms/" + room.getCode() + "/game");
    assertThat(destinations.get(2)).isEqualTo("/topic/rooms/" + room.getCode() + "/game");

    assertThat(payloadCaptor.getAllValues().get(0)).isInstanceOf(RoomSnapshotResponse.class);

    List<GameEventMessage> events = payloadCaptor.getAllValues().stream()
        .filter(GameEventMessage.class::isInstance)
        .map(GameEventMessage.class::cast)
        .toList();

    assertThat(events.get(0).type()).isEqualTo("ROUND_TIMEOUT");
    assertThat(events.get(0).gameFinished()).isFalse();
    assertThat(events.get(1).type()).isEqualTo("ROUND_STARTED");
    assertThat(events.get(1).currentDrawerId()).isEqualTo(String.valueOf(p2.getId()));
    assertThat(events.get(1).word()).isEqualTo("word-2");

    verify(gameScheduler).schedule(eq(room.getId()), any(Runnable.class), any(Instant.class));
    verify(gameSessionRepository, never()).remove(room.getId());
  }

  @Test
  void 게임종료_진행중이면완료표시() {
    Room room = Room.builder()
        .id(8L)
        .code("ROOM08")
        .status(RoomStatus.PLAYING)
        .createdAt(Instant.now())
        .build();

    GameSession session = GameSession.create(room.getId(), room.getCode(), 2, 60);
    session.start("word", "player", "player");

    when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
    when(roomRepository.save(room)).thenReturn(room);
    when(gameSessionRepository.getOrCreate(room)).thenReturn(session);

    gameService.endGame(room.getId());

    assertThat(session.getStatus()).isEqualTo(GameStatus.COMPLETED);
    assertThat(room.getStatus()).isEqualTo(RoomStatus.WAITING);
    verify(gameSessionRepository).remove(room.getId());
    verify(gameScheduler).cancel(room.getId());
  }

  @Test
  void 게임종료_대기상태면리셋() {
    Room room = Room.builder()
        .id(9L)
        .code("ROOM09")
        .status(RoomStatus.WAITING)
        .createdAt(Instant.now())
        .build();

    GameSession session = GameSession.create(room.getId(), room.getCode(), 0, 60);

    when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
    when(roomRepository.save(room)).thenReturn(room);
    when(gameSessionRepository.getOrCreate(room)).thenReturn(session);

    gameService.endGame(room.getId());

    assertThat(session.getStatus()).isEqualTo(GameStatus.IDLE);
    assertThat(room.getStatus()).isEqualTo(RoomStatus.WAITING);
    verify(gameSessionRepository).remove(room.getId());
  }

  private Player 플레이어생성(long id, Room room, String nickname) {
    return Player.builder()
        .id(id)
        .nickname(nickname)
        .room(room)
        .joinedAt(Instant.now())
        .build();
  }

  private void 트랜잭션템플릿스텁() {
    doAnswer(invocation -> {
      Consumer<TransactionStatus> action = invocation.getArgument(0);
      action.accept(null);
      return null;
    }).when(transactionTemplate).executeWithoutResult(any());
  }
}
