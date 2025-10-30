package com.catchmind_be.player.response;

import com.catchmind_be.player.entity.Player;

public record PlayerResponse(Long playerId, String nickname, boolean isHost, int score) {
  public static PlayerResponse from(Player player) {
    return new PlayerResponse(player.getId(), player.getNickname(), player.isHost(), player.getScore());
  }
}
