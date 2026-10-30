package com.meloo.room.realtime;

import java.util.UUID;

/** Room lifecycle callbacks invoked on that room's actor queue. */
public interface RoomConnectionHandler {

  void connected(UUID userId);

  void disconnected(UUID userId);

  static RoomConnectionHandler noOp() {
    return new RoomConnectionHandler() {
      @Override
      public void connected(UUID userId) {}

      @Override
      public void disconnected(UUID userId) {}
    };
  }
}
