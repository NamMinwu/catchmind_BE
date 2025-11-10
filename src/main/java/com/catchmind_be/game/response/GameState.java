package com.catchmind_be.game.response;

import com.catchmind_be.game.entity.GameStatus;
import com.catchmind_be.game.entity.GameSession;

public record GameState(
    GameStatus phase,
    int totalRounds,
    int currentRound,
    String currentDrawerId,
    String word
) {
  public static GameState toGameState(GameSession gameSession) {
    return new GameState(
        gameSession.getStatus(),
        gameSession.getTotalRounds(),
        gameSession.getCurrentRound(),
        gameSession.getCurrentPlayerId(),
        gameSession.getWord()
    );
  }
}
