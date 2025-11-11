package com.catchmind_be.game.entity;

import java.time.Instant;
import lombok.Getter;

@Getter
public class GameSession {

  private final Long roomId;
  private final String roomCode;
  private final int totalRounds;
  private final int secondsPerRound;
  private int currentRound;
  private String drawerOrder;
  private String currentDrawerId;
  private String word;
  private int currentOrderIndex;
  private Instant roundStartedAt;
  private Instant roundEndsAt;
  private GameStatus status;

  private GameSession(Long roomId, String roomCode,int totalRounds, int secondsPerRound) {
    this.roomId = roomId;
    this.roomCode = roomCode;
    this.totalRounds = Math.max(totalRounds, 0);
    this.secondsPerRound = Math.max(secondsPerRound, 0);
    resetToIdle();
  }

  public static GameSession create(Long roomId, String roomCode,  int totalRounds, int secondsPerRound) {
    return new GameSession(roomId, roomCode, totalRounds, secondsPerRound);
  }

  public void start(String word, String currentDrawerId, String drawerOrder) {
    Instant now = Instant.now();
    this.drawerOrder = drawerOrder;
    this.currentDrawerId = currentDrawerId;
    this.word = word;
    this.currentOrderIndex = 0;
    this.currentRound = 1;
    this.status = GameStatus.IN_PROGRESS;
    this.roundStartedAt = now;
    this.roundEndsAt = now.plusSeconds(secondsPerRound);
  }

  public void nextRound(String word, String currentDrawerId) {
    Instant now = Instant.now();
    this.word = word;
    this.currentDrawerId = currentDrawerId;
    this.currentOrderIndex += 1;
    this.currentRound += 1;
    this.roundStartedAt = now;
    this.roundEndsAt = now.plusSeconds(secondsPerRound);
  }


  public void markCompleted() {
    this.status = GameStatus.COMPLETED;
    this.roundStartedAt = null;
    this.roundEndsAt = null;
  }

  public void resetToIdle() {
    this.status = GameStatus.IDLE;
    this.currentRound = 0;
    this.roundStartedAt = null;
    this.roundEndsAt = null;
  }

}
