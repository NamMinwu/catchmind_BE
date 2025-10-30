package com.catchmind_be.player;

import com.catchmind_be.player.entity.Player;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRepository extends JpaRepository<Player, Long> {
  @Query("SELECT p FROM Player p " +
      "WHERE p.room.code = :roomCode " +
      "ORDER BY p.joinedAt ASC")
  List<Player> findPlayersByRoomCodeOrdered(String roomCode);

  @Query("select count(p) from Player p where p.room.code = :roomCode")
  long countByRoom_Code(String roomCode);




}
