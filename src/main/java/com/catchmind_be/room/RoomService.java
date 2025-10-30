package com.catchmind_be.room;

import com.catchmind_be.common.exception.CustomException;
import com.catchmind_be.common.exception.code.ErrorCode;
import com.catchmind_be.common.utils.RoomCodeGenerator;
import com.catchmind_be.player.PlayerRepository;
import com.catchmind_be.player.entity.Player;
import com.catchmind_be.player.response.PlayerResponse;
import com.catchmind_be.room.entity.Room;
import com.catchmind_be.room.response.JoinRoomResponse;
import com.catchmind_be.room.response.LeaveRoomResponse;
import java.security.SecureRandom;
import java.util.List;
import lombok.AllArgsConstructor;
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

  private final RoomRepository roomRepository;
  private final PlayerRepository playerRepository;
  private final RoomCodeGenerator roomCodeGenerator;

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
  public Room getRoom(String code) {
    return roomRepository.findByCode(code).orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
  }

  @Transactional
  public JoinRoomResponse joinRoom(String roomCode, String nickname) {
    Room room = getRoom(roomCode);
    String normalizedNickname = normalizeNickname(nickname, PLAYER_FALLBACK_PREFIX);
    Player newPlayer = Player.builder()
        .nickname(normalizedNickname)
        .isHost(false)
        .build();

    room.addPlayer(newPlayer);

    playerRepository.saveAndFlush(newPlayer);

    List<PlayerResponse> players = playerRepository.findPlayersByRoomCodeOrdered(room.getCode())
        .stream()
        .map(PlayerResponse::from).toList();

    return new JoinRoomResponse(
        room.getCode(),
        String.valueOf(newPlayer.getId()),
        false,
        players,
        room.getStatus().name()
    );
  }

  @Transactional
  public LeaveRoomResponse leaveRoom(String roomCode, String playerId) {
    Room room = getRoom(roomCode);
    Long playerIdAsLong = parsePlayerId(playerId);
    Player player = playerRepository.findById(playerIdAsLong)
        .filter(player1 -> player1.getRoom().getCode().equals(roomCode))
        .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));
    boolean wasHost = player.isHost();

    room.getPlayers().remove(player); //orphan 덕분에 바로 삭제

    long remaining = playerRepository.countByRoom_Code(roomCode);


    if(remaining == 0){
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
}
