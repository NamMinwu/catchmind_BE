package com.catchmind_be.game.response;

import com.catchmind_be.game.entity.GameSession;

public record GuessResult(
    boolean correct,
    GameSession gameSession
) {
  public static GuessResult inCorrect() {
    return new GuessResult(
        false,
        null
    );
  }
}
