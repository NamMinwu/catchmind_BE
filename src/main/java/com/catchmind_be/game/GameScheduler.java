package com.catchmind_be.game;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class GameScheduler {

  private final ThreadPoolTaskScheduler taskScheduler;
  private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

  public GameScheduler(@Qualifier("threadPoolTaskScheduler") ThreadPoolTaskScheduler taskScheduler) {
    this.taskScheduler = taskScheduler;
  }

  public void schedule(Long roomId, Runnable task, Instant startAt) {
    cancel(roomId);
    ScheduledFuture<?> future = taskScheduler.schedule(task, startAt);
    scheduledTasks.put(roomId, future);
  }

  public void cancel(Long roomId) {
    ScheduledFuture<?> future = scheduledTasks.remove(roomId);
    if (future != null) {
      future.cancel(false);
    }
  }

  public void clear(Long roomId) {
    scheduledTasks.remove(roomId);
  }
}
