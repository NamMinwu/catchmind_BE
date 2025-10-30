package com.catchmind_be.player.entity;

import com.catchmind_be.room.entity.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name ="player")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Player {
  @Id
  @GeneratedValue
  private Long id;

  @Column(nullable = false)
  private String nickname;

  @Column(nullable = false)
  private Instant joinedAt;

  @Builder.Default
  private Integer score = 0;

  private boolean isHost;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "room_id", nullable = false)
  private Room room;

  @PrePersist
  void onCreate() {
    if(joinedAt == null) this.joinedAt = Instant.now();
    if(score == null) this.score = 0;
  }


}
