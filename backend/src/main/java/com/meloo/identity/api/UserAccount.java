package com.meloo.identity.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record UserAccount(
    UUID id,
    String kind,
    String role,
    String status,
    String nickname,
    @Nullable String email,
    @Nullable String lockReason,
    @Nullable Instant lockedAt,
    Instant lastActiveAt,
    List<String> linkedProviders,
    Instant createdAt) {
  public UserAccount {
    linkedProviders = List.copyOf(linkedProviders);
  }
}
