package com.meloo.chat.api;

import java.time.Instant;
import java.util.UUID;

public record ChatContextMessage(
    UUID messageId, UUID senderId, String senderNickname, String content, Instant sentAt) {}
