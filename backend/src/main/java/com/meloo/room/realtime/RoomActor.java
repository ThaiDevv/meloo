package com.meloo.room.realtime;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.springframework.scheduling.TaskScheduler;

/** Serial command queue for one room. Timer callbacks are submitted back to the same queue. */
public final class RoomActor implements AutoCloseable {

  private final ExecutorService queue;
  private final TaskScheduler scheduler;
  private final Set<ScheduledFuture<?>> timers = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean closed = new AtomicBoolean();

  RoomActor(UUID roomId, TaskScheduler scheduler) {
    this.scheduler = scheduler;
    this.queue =
        Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("meloo-room-" + roomId + "-").factory());
  }

  public <T> CompletableFuture<T> submit(Supplier<T> command) {
    CompletableFuture<T> result = new CompletableFuture<>();
    if (closed.get()) {
      return CompletableFuture.failedFuture(new IllegalStateException("Room actor is closed"));
    }
    try {
      queue.execute(
          () -> {
            if (closed.get()) {
              result.completeExceptionally(new IllegalStateException("Room actor is closed"));
              return;
            }
            try {
              result.complete(command.get());
            } catch (Throwable failure) {
              result.completeExceptionally(failure);
            }
          });
    } catch (RejectedExecutionException failure) {
      result.completeExceptionally(new IllegalStateException("Room actor is closed", failure));
    }
    return result;
  }

  public CompletableFuture<Void> execute(Runnable command) {
    return submit(
        () -> {
          command.run();
          return null;
        });
  }

  public ScheduledFuture<?> schedule(Duration delay, Runnable command) {
    if (delay == null || delay.isNegative() || delay.isZero()) {
      throw new IllegalArgumentException("Room timer delay must be positive");
    }
    if (closed.get()) {
      throw new IllegalStateException("Room actor is closed");
    }

    AtomicReference<ScheduledFuture<?>> timerRef = new AtomicReference<>();
    ScheduledFuture<?> timer =
        scheduler.schedule(
            () -> {
              ScheduledFuture<?> scheduled = timerRef.get();
              if (scheduled != null) {
                timers.remove(scheduled);
              }
              execute(command);
            },
            Instant.now().plus(delay));
    timerRef.set(timer);
    timers.add(timer);
    if (timer.isDone()) {
      timers.remove(timer);
    }
    if (closed.get() && timers.remove(timer)) {
      timer.cancel(false);
    }
    return timer;
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      timers.forEach(timer -> timer.cancel(false));
      timers.clear();
      queue.shutdownNow();
    }
  }
}
