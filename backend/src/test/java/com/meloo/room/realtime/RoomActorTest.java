package com.meloo.room.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class RoomActorTest {

  private ThreadPoolTaskScheduler scheduler;
  private RoomActor actor;

  @BeforeEach
  void setUp() {
    scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.initialize();
    actor = new RoomActor(UUID.randomUUID(), scheduler);
  }

  @AfterEach
  void tearDown() {
    actor.close();
    scheduler.shutdown();
  }

  @Test
  void concurrentCommandsRunOneAtATimeOnTheRoomQueue() throws Exception {
    AtomicInteger activeCommands = new AtomicInteger();
    AtomicInteger maxActiveCommands = new AtomicInteger();
    AtomicInteger completedCommands = new AtomicInteger();
    CountDownLatch start = new CountDownLatch(1);
    List<CompletableFuture<Void>> commands = new CopyOnWriteArrayList<>();

    try (var producers = Executors.newFixedThreadPool(2)) {
      for (int producer = 0; producer < 2; producer++) {
        producers.submit(
            () -> {
              start.await();
              for (int index = 0; index < 50; index++) {
                commands.add(
                    actor.execute(
                        () -> {
                          int active = activeCommands.incrementAndGet();
                          maxActiveCommands.accumulateAndGet(active, Math::max);
                          completedCommands.incrementAndGet();
                          activeCommands.decrementAndGet();
                        }));
              }
              return null;
            });
      }
      start.countDown();
      producers.shutdown();
      assertThat(producers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    CompletableFuture.allOf(commands.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);

    assertThat(completedCommands).hasValue(100);
    assertThat(maxActiveCommands).hasValue(1);
  }

  @Test
  void scheduledCommandRunsOnTheSameRoomQueue() throws Exception {
    String commandThread =
        actor.submit(() -> Thread.currentThread().getName()).get(5, TimeUnit.SECONDS);
    CompletableFuture<String> timerThread = new CompletableFuture<>();

    actor.schedule(
        Duration.ofMillis(10), () -> timerThread.complete(Thread.currentThread().getName()));

    assertThat(timerThread.get(5, TimeUnit.SECONDS)).isEqualTo(commandThread);
  }
}
