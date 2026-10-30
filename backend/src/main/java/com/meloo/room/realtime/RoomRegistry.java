package com.meloo.room.realtime;

import com.meloo.room.api.RoomDescriptor;
import com.meloo.room.api.RoomDirectory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class RoomRegistry implements RoomDirectory, AutoCloseable {

  private final ConcurrentMap<UUID, RoomRuntime> rooms = new ConcurrentHashMap<>();
  private final Map<String, Set<RoomUser>> sessionRooms = new HashMap<>();
  private final Map<RoomUser, Set<String>> userSessions = new HashMap<>();
  private final TaskScheduler scheduler;

  public RoomRegistry(@Qualifier("roomRealtimeScheduler") TaskScheduler roomRealtimeScheduler) {
    this.scheduler = roomRealtimeScheduler;
  }

  public synchronized RoomActor register(RoomDescriptor descriptor, RoomConnectionHandler handler) {
    RoomRuntime runtime =
        new RoomRuntime(descriptor, new RoomActor(descriptor.roomId(), scheduler), handler);
    RoomRuntime existing = rooms.putIfAbsent(descriptor.roomId(), runtime);
    if (existing != null) {
      runtime.actor().close();
      throw new IllegalStateException("Room is already registered: " + descriptor.roomId());
    }
    return runtime.actor();
  }

  public RoomActor register(RoomDescriptor descriptor) {
    return register(descriptor, RoomConnectionHandler.noOp());
  }

  public Optional<RoomActor> actor(UUID roomId) {
    return Optional.ofNullable(rooms.get(roomId)).map(RoomRuntime::actor);
  }

  public CompletableFuture<Void> update(RoomDescriptor descriptor) {
    RoomRuntime runtime = rooms.get(descriptor.roomId());
    if (runtime == null) {
      throw new IllegalArgumentException("Room is not registered: " + descriptor.roomId());
    }
    return runtime.actor().execute(() -> runtime.descriptor().set(descriptor));
  }

  @Override
  public Optional<RoomDescriptor> find(UUID roomId) {
    return Optional.ofNullable(rooms.get(roomId)).map(runtime -> runtime.descriptor().get());
  }

  synchronized void trackSession(UUID roomId, UUID userId, String sessionId) {
    RoomRuntime runtime = rooms.get(roomId);
    if (runtime == null || !runtime.descriptor().get().hasMember(userId)) {
      return;
    }

    RoomUser roomUser = new RoomUser(roomId, userId);
    Set<String> sessions = userSessions.computeIfAbsent(roomUser, ignored -> new HashSet<>());
    boolean newlyConnected = sessions.isEmpty();
    if (sessions.add(sessionId)) {
      sessionRooms.computeIfAbsent(sessionId, ignored -> new HashSet<>()).add(roomUser);
      if (newlyConnected) {
        runtime.actor().execute(() -> runtime.connectionHandler().connected(userId));
      }
    }
  }

  synchronized void disconnectSession(String sessionId) {
    Set<RoomUser> roomUsers = sessionRooms.remove(sessionId);
    if (roomUsers == null) {
      return;
    }
    for (RoomUser roomUser : roomUsers) {
      Set<String> sessions = userSessions.get(roomUser);
      if (sessions == null) {
        continue;
      }
      sessions.remove(sessionId);
      if (!sessions.isEmpty()) {
        continue;
      }
      userSessions.remove(roomUser);
      RoomRuntime runtime = rooms.get(roomUser.roomId());
      if (runtime != null) {
        runtime.actor().execute(() -> runtime.connectionHandler().disconnected(roomUser.userId()));
      }
    }
  }

  public synchronized void remove(UUID roomId) {
    RoomRuntime runtime = rooms.remove(roomId);
    if (runtime == null) {
      return;
    }
    for (Map.Entry<String, Set<RoomUser>> entry : new ArrayList<>(sessionRooms.entrySet())) {
      entry.getValue().removeIf(roomUser -> roomUser.roomId().equals(roomId));
      if (entry.getValue().isEmpty()) {
        sessionRooms.remove(entry.getKey());
      }
    }
    userSessions.keySet().removeIf(roomUser -> roomUser.roomId().equals(roomId));
    runtime.actor().close();
  }

  @Override
  public synchronized void close() {
    rooms.values().forEach(runtime -> runtime.actor().close());
    rooms.clear();
    sessionRooms.clear();
    userSessions.clear();
  }

  private record RoomRuntime(
      AtomicReference<RoomDescriptor> descriptor,
      RoomActor actor,
      RoomConnectionHandler connectionHandler) {

    private RoomRuntime(
        RoomDescriptor descriptor, RoomActor actor, RoomConnectionHandler connectionHandler) {
      this(new AtomicReference<>(descriptor), actor, connectionHandler);
    }
  }

  private record RoomUser(UUID roomId, UUID userId) {}
}
