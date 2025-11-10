package com.catchmind_be.game;

import com.catchmind_be.game.entity.GameSession;
import com.catchmind_be.room.entity.Room;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class MemoryGameSessionRepository implements GameSessionRepository {

  private final Map<Long, GameSession> roundStates = new ConcurrentHashMap<>();

  @Override
  public GameSession create(Long roomId, String roomCode, int totalRounds, int secondsPerRound) {
    GameSession state = GameSession.create(roomId,roomCode,totalRounds, secondsPerRound);
    roundStates.put(roomId, state);
    return state;
  }

  @Override
  public GameSession getOrCreate(Room room) {
    GameSession gameSession = roundStates.get(room.getId());
    if (gameSession == null) {
      return create(room.getId(), room.getCode(), 0, 0);
    }
    return gameSession;
  }

  @Override
  public void remove(Long roomId) {
    roundStates.remove(roomId);
  }
}
