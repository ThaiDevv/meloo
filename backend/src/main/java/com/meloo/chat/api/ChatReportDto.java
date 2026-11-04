package com.meloo.chat.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ChatReportDto(
    UUID id,
    String messageText,
    UUID senderId,
    String senderNickname,
    UUID reporterId,
    List<ChatContextMessage> context,
    ReportStatus status,
    @Nullable UUID handledBy,
    @Nullable Instant handledAt,
    Instant createdAt) {}
