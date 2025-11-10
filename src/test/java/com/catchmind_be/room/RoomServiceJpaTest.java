package com.catchmind_be.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.catchmind_be.common.utils.RoomCodeGenerator;
import com.catchmind_be.game.GameService;
import com.catchmind_be.game.GameSessionRepository;
import com.catchmind_be.game.MemoryGameSessionRepository;
import com.catchmind_be.player.PlayerRepository;
import com.catchmind_be.player.entity.Player;
import com.catchmind_be.player.response.PlayerResponse;
import com.catchmind_be.room.entity.Room;
import com.catchmind_be.room.response.LeaveRoomResponse;
import com.catchmind_be.room.response.RoomSnapshotResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({RoomService.class, RoomServiceJpaTest.TestConfig.class})
class RoomServiceJpaTest {

  @Autowired
  private RoomService roomService;

  @Autowired
  private RoomRepository roomRepository;

  @Autowired
  private PlayerRepository playerRepository;

  @Test
  void 방을_만들면_호스트가_생기고_hostPlayerId가_세팅된다() {
    Room saved = roomService.createRoom("host-nick");

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCode()).isNotBlank();

    Room reloaded = roomRepository.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getHostPlayerId()).isNotBlank();

    assertThat(reloaded.getPlayers()).hasSize(1);
    Player host = reloaded.getPlayers().get(0);
    assertThat(host.isHost()).isTrue();
    assertThat(reloaded.getHostPlayerId()).isEqualTo(String.valueOf(host.getId()));
    assertThat(host.getNickname()).isEqualTo("host-nick");
  }

  @Test
  void 방_코드는_중복되지_않는다() {
    Room room1 = roomService.createRoom("first");
    Room room2 = roomService.createRoom("second");

    assertThat(room1.getCode()).isNotBlank();
    assertThat(room2.getCode()).isNotBlank();
    assertThat(room1.getCode()).isNotEqualTo(room2.getCode());
  }

  @Test
  void 마지막_한명이_나가면_방이_삭제된다() {
    Room room = roomService.createRoom("host22");
    String roomCode = room.getCode();
    String hostId = room.getHostPlayerId();

    LeaveRoomResponse response = roomService.leaveRoom(roomCode, hostId);

    assertThat(response.roomCode()).isEqualTo(roomCode);
    assertThat(response.roomDeleted()).isTrue();
    assertThat(response.newHostPlayerId()).isNull();
    assertThat(response.remainingPlayers()).isZero();

    Optional<Room> maybeRoom = roomRepository.findByCode(roomCode);
    assertThat(maybeRoom).isEmpty();

    assertThat(playerRepository.findById(Long.valueOf(hostId))).isEmpty();
  }



  @Test
  void 조인하면_플레이어가_추가되고_응답에_정상반영된다() {
    Room created = roomService.createRoom("host-nick");
    String roomCode = created.getCode();

    RoomSnapshotResponse response = roomService.joinRoom(roomCode, "guest");

    assertThat(response.roomCode()).isEqualTo(roomCode);
    assertThat(response.players()).hasSize(2);
    assertThat(response.players())
        .extracting(PlayerResponse::nickname)
        .containsExactlyInAnyOrder("host-nick", "guest");

    List<Player> playersInRoom = playerRepository.findPlayersByRoomCodeOrdered(roomCode);
    assertThat(playersInRoom).hasSize(2);
    Player host = playersInRoom.stream().filter(Player::isHost).findFirst().orElseThrow();
    Player guest = playersInRoom.stream().filter(player -> !player.isHost()).findFirst().orElseThrow();

    assertThat(host.getNickname()).isEqualTo("host-nick");
    assertThat(guest.getNickname()).isEqualTo("guest");

    Long responseGuestId = response.players().stream()
        .filter(player -> !player.isHost())
        .map(PlayerResponse::playerId)
        .findFirst()
        .orElseThrow();
    assertThat(responseGuestId).isEqualTo(guest.getId());
  }



  @Test
  void 일반_플레이어가_나가면_방은_유지되고_새_호스트는_선정되지_않는다() {
    Room room = roomService.createRoom("host");
    String roomCode = room.getCode();
    String hostId = room.getHostPlayerId();

    RoomSnapshotResponse guestJoin = roomService.joinRoom(roomCode, "guest");
    String guestId = guestJoin.players().stream()
        .filter(player -> !player.isHost())
        .map(player -> String.valueOf(player.playerId()))
        .findFirst()
        .orElseThrow();

    LeaveRoomResponse res = roomService.leaveRoom(roomCode, guestId);

    assertThat(res.roomCode()).isEqualTo(roomCode);
    assertThat(res.roomDeleted()).isFalse();
    assertThat(res.newHostPlayerId()).isNull();
    assertThat(res.remainingPlayers()).isEqualTo(1);

    Room reloaded = roomRepository.findByCode(roomCode).orElseThrow();
    assertThat(reloaded.getHostPlayerId()).isEqualTo(hostId);
    List<Player> remaining = playerRepository.findPlayersByRoomCodeOrdered(roomCode);
    assertThat(remaining).hasSize(1);
    Player only = remaining.get(0);
    assertThat(only.getId()).isEqualTo(Long.valueOf(hostId));
    assertThat(only.isHost()).isTrue();
  }

  @Test
  void 호스트가_나가면_남은_플레이어_중_새_호스트가_선정된다() {
    // given: 방 생성 + 게스트 2명 조인
    Room room = roomService.createRoom("host");
    String roomCode = room.getCode();
    String oldHostId = room.getHostPlayerId();

    RoomSnapshotResponse g1 = roomService.joinRoom(roomCode, "u1");
    RoomSnapshotResponse g2 = roomService.joinRoom(roomCode, "u2");

    // when: 호스트 퇴장
    LeaveRoomResponse res = roomService.leaveRoom(roomCode, oldHostId);

    // then
    assertThat(res.roomCode()).isEqualTo(roomCode);
    assertThat(res.roomDeleted()).isFalse();
    assertThat(res.remainingPlayers()).isEqualTo(2);
    assertThat(res.newHostPlayerId()).isNotNull();

    Room newRoom = roomRepository.findByCode(roomCode).orElseThrow();
    assertThat(newRoom.getHostPlayerId()).isEqualTo(res.newHostPlayerId());
    assertThat(newRoom.getPlayers()).hasSize(2);

    Player newHost = newRoom.getPlayers().stream().filter(Player::isHost).findFirst().orElseThrow();
    assertThat(newHost.getNickname()).isIn("u1", "u2");
  }

  @TestConfiguration
  static class TestConfig {
    @Bean
    RoomCodeGenerator roomCodeGenerator() {
      return new RoomCodeGenerator();
    }

    @Bean
    GameSessionRepository gameSessionRepository() {
      return new MemoryGameSessionRepository();
    }

    @Bean
    GameService gameService() {
      return Mockito.mock(GameService.class);
    }

    @Bean
    SimpMessagingTemplate simpMessagingTemplate() {
      return Mockito.mock(SimpMessagingTemplate.class);
    }
  }
}
