package com.meloo.room.realtime;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;

/** Serial command queue for one room. Timer callbacks are submitted back to the same queue. */
public final class RoomActor implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(RoomActor.class);

  private final UUID roomId;
  private final ExecutorService queue;
  private final TaskScheduler scheduler;
  private final Set<ScheduledFuture<?>> timers = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean closed = new AtomicBoolean();
  private final Object lifecycleLock = new Object();
  private CompletableFuture<Void> termination;

  RoomActor(UUID roomId, TaskScheduler scheduler) {
    this.roomId = roomId;
    this.scheduler = scheduler;
    this.queue =
        Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("meloo-room-" + roomId + "-").factory());
  }

  public <T> CompletableFuture<T> submit(Supplier<T> command) {
    CompletableFuture<T> result = new CompletableFuture<>();
    synchronized (lifecycleLock) {
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
    synchronized (lifecycleLock) {
      if (closed.get()) {
        throw new IllegalStateException("Room actor is closed");
      }

      AtomicReference<TimerHandle> timerRef = new AtomicReference<>();
      AtomicBoolean timerFired = new AtomicBoolean();
      ScheduledFuture<?> timer =
          scheduler.schedule(
              () -> {
                timerFired.set(true);
                TimerHandle scheduled = timerRef.get();
                if (scheduled != null) {
                  timers.remove(scheduled);
                }
                execute(command)
                    .whenComplete(
                        (ignored, failure) -> {
                          if (failure != null) {
                            log.error(
                                "Room timer command failed for room {} (exceptionType={})",
                                roomId,
                                failure.getClass().getName());
                          }
                        });
              },
              Instant.now().plus(delay));
      TimerHandle handle = new TimerHandle(timer, () -> timers.remove(timerRef.get()));
      timerRef.set(handle);
      timers.add(handle);
      if (timerFired.get() || timer.isDone()) {
        timers.remove(handle);
      }
      return handle;
    }
  }

  int pendingTimerCount() {
    return timers.size();
  }

  /** Stops accepting commands and queues a terminal barrier after all accepted work. */
  public CompletableFuture<Void> closeAsync() {
    synchronized (lifecycleLock) {
      if (termination != null) {
        return termination;
      }
      closed.set(true);
      timers.forEach(timer -> timer.cancel(false));
      timers.clear();
      termination = new CompletableFuture<>();
      try {
        queue.execute(
            () -> {
              queue.shutdown();
              termination.complete(null);
            });
      } catch (RejectedExecutionException failure) {
        queue.shutdown();
        termination.completeExceptionally(failure);
      }
      return termination;
    }
  }

  @Override
  public void close() {
    closeAsync();
  }

  private static final class TimerHandle implements ScheduledFuture<Object> {

    private final ScheduledFuture<?> delegate;
    private final Runnable onCancelled;

    private TimerHandle(ScheduledFuture<?> delegate, Runnable onCancelled) {
      this.delegate = delegate;
      this.onCancelled = onCancelled;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      boolean cancelled = delegate.cancel(mayInterruptIfRunning);
      if (cancelled) {
        onCancelled.run();
      }
      return cancelled;
    }

    @Override
    public boolean isCancelled() {
      return delegate.isCancelled();
    }

    @Override
    public boolean isDone() {
      return delegate.isDone();
    }

    @Override
    public Object get() throws InterruptedException, ExecutionException {
      return delegate.get();
    }

    @Override
    public Object get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return delegate.get(timeout, unit);
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return delegate.getDelay(unit);
    }

    @Override
    public int compareTo(Delayed other) {
      return delegate.compareTo(other);
    }
  }
}
