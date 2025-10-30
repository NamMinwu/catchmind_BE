package com.catchmind_be.room.entity;

import com.catchmind_be.player.entity.Player;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Room {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false, unique = true)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RoomStatus status = RoomStatus.WAITING;

  private String hostPlayerId;

  @Builder.Default
  private Integer maxPlayers = 5;

  @Builder.Default
  private Integer round = 3;

  @Builder.Default
  @OneToMany(mappedBy = "room", fetch = FetchType.LAZY,
      cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Player> players = new ArrayList<>();

  public void addPlayer(Player player) {
    if (players == null) {
      players = new ArrayList<>();
    }
    players.add(player);
    player.setRoom(this);
  }


  @PrePersist
  void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
    if (maxPlayers == null) maxPlayers = 5;
    if (round == null) round = 3;
  }
}
