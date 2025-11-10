package com.catchmind_be.room;

import com.catchmind_be.common.exception.CustomException;
import com.catchmind_be.common.exception.code.ErrorCode;
import com.catchmind_be.common.utils.RoomCodeGenerator;
import com.catchmind_be.game.GameService;
import com.catchmind_be.game.GameSessionRepository;
import com.catchmind_be.game.entity.GameSession;
import com.catchmind_be.game.response.GameEventMessage;
import com.catchmind_be.player.PlayerRepository;
import com.catchmind_be.player.entity.Player;
import com.catchmind_be.player.response.PlayerResponse;
import com.catchmind_be.room.entity.Room;
import com.catchmind_be.room.response.RoomSnapshotResponse;
import com.catchmind_be.room.response.LeaveRoomResponse;
import java.security.SecureRandom;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@AllArgsConstructor
public class RoomService {

  private static final String HOST_FALLBACK_PREFIX = "Host";
  private static final String PLAYER_FALLBACK_PREFIX = "Player";
  private static final int ROOM_CODE_LENGTH = 6;
  private static final int RANDOM_SUFFIX_RANGE = 9000;

  private final GameSessionRepository gameSessionRepository;
  private final RoomRepository roomRepository;
  private final PlayerRepository playerRepository;
  private final RoomCodeGenerator roomCodeGenerator;
  private final SimpMessagingTemplate messagingTemplate;
  private final GameService gameService;

  private final SecureRandom random = new SecureRandom();

  @Transactional
  public Room createRoom(String nickname) {
    String normalizedNickname = normalizeNickname(nickname, HOST_FALLBACK_PREFIX);

    Room room = Room.builder()
        .code(generateUniqueRoomCode())
        .build();
    Player hostPlayer = Player.builder()
        .nickname(normalizedNickname)
        .isHost(true)
        .build();
    room.addPlayer(hostPlayer);

    Room savedRoom = roomRepository.saveAndFlush(room);
    savedRoom.setHostPlayerId(String.valueOf(hostPlayer.getId()));

    return savedRoom;
  }


  @Transactional(readOnly = true)
  public RoomSnapshotResponse getRoom(String code) {
    Room room = roomRepository.findByCode(code).orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
    return buildRoomSnapShotResponse(room);
  }

  public RoomSnapshotResponse buildRoomSnapShotResponse(Room room) {
    List<PlayerResponse> players = playerRepository.findPlayersByRoomCodeOrdered(room.getCode())
        .stream()
        .map(PlayerResponse::from).toList();

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



  @Transactional
  public RoomSnapshotResponse joinRoom(String roomCode, String nickname) {
    Room room = roomRepository.findByCode(roomCode).orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
    String normalizedNickname = normalizeNickname(nickname, PLAYER_FALLBACK_PREFIX);
    Player newPlayer = Player.builder()
        .nickname(normalizedNickname)
        .isHost(false)
        .build();

    room.addPlayer(newPlayer);
    playerRepository.saveAndFlush(newPlayer);

    return buildRoomSnapShotResponse(room);
  }


  @Transactional
  public LeaveRoomResponse leaveRoom(String roomCode, String playerId) {
    Room room = roomRepository.findByCode(roomCode).orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));;
    Long playerIdAsLong = parsePlayerId(playerId);
    Player player = playerRepository.findById(playerIdAsLong)
        .filter(player1 -> player1.getRoom().getCode().equals(roomCode))
        .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));
    boolean wasHost = player.isHost();

    room.getPlayers().remove(player); //orphan 덕분에 바로 삭제

    long remaining = playerRepository.countByRoom_Code(roomCode);


    if(remaining == 0){
      Long roomId = room.getId();
      gameService.endGame(roomId);
      roomRepository.delete(room);
      return new LeaveRoomResponse(
          roomCode,
          true,
          null,
          0
      );
    }

    String newHostId = null;
    if(wasHost){
      newHostId = assignNewHost(room);
    }

    return new LeaveRoomResponse(
        roomCode,
        false,
        newHostId,
        remaining
    );
  }

  private String generateUniqueRoomCode() {
    String code;
    do {
      code = roomCodeGenerator.generateCode(ROOM_CODE_LENGTH);
    } while (roomRepository.findByCode(code).isPresent());
    return code;
  }

  private String assignNewHost(Room room){
    List<Player> players = playerRepository.findPlayersByRoomCodeOrdered(room.getCode());
    if (players.isEmpty()) {
      room.setHostPlayerId(null);
      return null;
    }

    Player newHostPlayer = players.get(0);
    for (Player player : players) {
      player.setHost(
          player.getId().equals(newHostPlayer.getId())
      );
    }

    playerRepository.saveAll(players);
    room.setHostPlayerId(String.valueOf(newHostPlayer.getId()));

    return room.getHostPlayerId();
  }

  private String normalizeNickname(String nickname, String fallbackPrefix) {
    if (StringUtils.hasText(nickname)) {
      return nickname.trim();
    }
    return fallbackPrefix + (1000 + random.nextInt(RANDOM_SUFFIX_RANGE));
  }



  private Long parsePlayerId(String rawPlayerId) {
    if (!StringUtils.hasText(rawPlayerId)) {
      throw new CustomException(ErrorCode.PLAYER_NOT_FOUND);
    }
    try {
      return Long.valueOf(rawPlayerId);
    } catch (NumberFormatException exception) {
      throw new CustomException(ErrorCode.PLAYER_NOT_FOUND);
    }
  }

  public void broadcastState(RoomSnapshotResponse roomSnapshotResponse) {
    messagingTemplate.convertAndSend("/topic/rooms/" + roomSnapshotResponse.roomCode() + "/state",
        roomSnapshotResponse);
  }

  public void broadcastGameEvent(String roomCode, GameEventMessage startEvent){
    messagingTemplate.convertAndSend("/topic/rooms/" + roomCode + "/game", startEvent);
  }
}
