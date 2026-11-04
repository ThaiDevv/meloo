package com.meloo.chat.api;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageEvent(
    String type,
    UUID messageId,
    UUID senderId,
    String senderNickname,
    String content,
    Instant sentAt,
    Instant serverTime) {

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
