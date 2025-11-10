package com.catchmind_be.game;

import com.catchmind_be.game.entity.GameSession;
import com.catchmind_be.room.entity.Room;

public interface GameSessionRepository {
  GameSession create(Long roomId, String roomCode, int totalRounds, int secondsPerRound);
  GameSession getOrCreate(Room room);
  void remove(Long roomId);
}
