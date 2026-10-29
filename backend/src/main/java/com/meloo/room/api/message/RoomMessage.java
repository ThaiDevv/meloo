package com.meloo.room.api.message;

import java.time.Instant;

/** Interface for all server-to-client realtime STOMP events. */
public interface RoomMessage {
  String type();

  Instant serverTime();
}
