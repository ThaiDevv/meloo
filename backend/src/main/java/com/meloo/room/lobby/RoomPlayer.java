package com.meloo.room.lobby;

import com.meloo.generated.model.RoomPlayerSummaryDto;
import com.meloo.room.api.message.RoomPlayerSummaryPayload;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Đại diện cho trạng thái của một người chơi trong phòng sảnh. */
public final class RoomPlayer {

  private final UUID userId;
  private final String nickname;
  private final @Nullable String avatarUrl;
  private final @Nullable String avatarId;
  private final boolean isGuest;
  private final Instant joinedAt;
  private volatile boolean isHost;
  private volatile boolean isReady;
  private volatile boolean isConnected;
  private volatile int score;

  public RoomPlayer(
      UUID userId,
      String nickname,
      @Nullable String avatarUrl,
      boolean isHost,
      boolean isReady,
      boolean isGuest,
      boolean isConnected,
      int score,
      Instant joinedAt) {
    this(userId, nickname, avatarUrl, null, isHost, isReady, isGuest, isConnected, score, joinedAt);
  }

  public RoomPlayer(
      UUID userId,
      String nickname,
      @Nullable String avatarUrl,
      @Nullable String avatarId,
      boolean isHost,
      boolean isReady,
      boolean isGuest,
      boolean isConnected,
      int score,
      Instant joinedAt) {
    this.userId = Objects.requireNonNull(userId, "userId must not be null");
    this.nickname = Objects.requireNonNull(nickname, "nickname must not be null");
    this.avatarUrl = avatarUrl;
    this.avatarId = avatarId;
    this.isHost = isHost;
    this.isReady = isReady;
    this.isGuest = isGuest;
    this.isConnected = isConnected;
    this.score = score;
    this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt must not be null");
  }

  public UUID userId() {
    return userId;
  }

  public String nickname() {
    return nickname;
  }

  public @Nullable String avatarUrl() {
    return avatarUrl;
  }

  public boolean isHost() {
    return isHost;
  }

  public void setHost(boolean isHost) {
    this.isHost = isHost;
  }

  public boolean isReady() {
    return isReady;
  }

  public void setReady(boolean isReady) {
    this.isReady = isReady;
  }

  public boolean isGuest() {
    return isGuest;
  }

  public boolean isConnected() {
    return isConnected;
  }

  public void setConnected(boolean isConnected) {
    this.isConnected = isConnected;
  }

  public int score() {
    return score;
  }

  public void setScore(int score) {
    this.score = score;
  }

  public Instant joinedAt() {
    return joinedAt;
  }

  public RoomPlayerSummaryPayload toPayload() {
    return new RoomPlayerSummaryPayload(
        userId, nickname, avatarUrl, avatarId, isHost, isReady, isGuest, isConnected, score);
  }

  public RoomPlayerSummaryDto toDto() {
    RoomPlayerSummaryDto dto =
        new RoomPlayerSummaryDto(userId, nickname, isHost, isReady, isGuest, isConnected, score);
    dto.setAvatarUrl(avatarUrl);
    dto.setAvatarId(
        avatarId == null ? null : RoomPlayerSummaryDto.AvatarIdEnum.fromValue(avatarId));
    return dto;
  }
}
