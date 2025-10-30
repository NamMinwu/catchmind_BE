package com.catchmind_be.room;

import com.catchmind_be.room.entity.Room;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoomRepository extends JpaRepository<Room, Long> {
  Optional<Room> findByCode(String code);
}
