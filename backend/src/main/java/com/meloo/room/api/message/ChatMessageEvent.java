package com.meloo.room.api.message;

import java.time.Instant;
import java.util.UUID;

/** In-room chat broadcast (BR-16, BR-17). */
public record ChatMessageEvent(
    String type,
    UUID messageId,
    UUID senderId,
    String senderNickname,
    String content,
    Instant sentAt,
    Instant serverTime)
    implements RoomMessage {

  public ChatMessageEvent(
      UUID messageId,
      UUID senderId,
      String senderNickname,
      String content,
      Instant sentAt,
      Instant serverTime) {
    this("CHAT_MESSAGE", messageId, senderId, senderNickname, content, sentAt, serverTime);
  }
}
