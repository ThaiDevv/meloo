package com.meloo.room.lobby;

import com.meloo.generated.model.RoomPlayerSummaryDto;
import com.meloo.generated.model.RoomResponseDto;
import com.meloo.generated.model.RoomStatusDto;
import com.meloo.generated.model.RoomTypeDto;
import com.meloo.generated.model.SoloFilterDto;
import com.meloo.room.api.message.RoomFilterPayload;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.jspecify.annotations.Nullable;

/**
 * Trạng thái nội bộ của một phòng chơi trong bộ nhớ, được quản lý tuần tự bên trong RoomActor.
 *
 * <p>Tuân thủ BR-11 (mã phòng, số người 2-5), BR-12 (chủ phòng, sẵn sàng, mời ra), BR-13
 * (QUICK_MATCH), BR-14 (giữ chỗ 30s khi mất kết nối), BR-15 (xếp chờ khi đang chơi, phòng trống 10
 * phút tự đóng).
 */
public final class RoomSession {

  private final UUID roomId;
  private final @Nullable String code;
  private final RoomTypeDto roomType;
  private final Instant createdAt;
  private final List<RoomPlayer> players = new ArrayList<>();
  private final List<RoomPlayer> waitingPlayers = new ArrayList<>();
  private final Set<UUID> bannedUserIds = new HashSet<>();
  private final Map<UUID, ScheduledFuture<?>> disconnectTimers = new ConcurrentHashMap<>();

  private @Nullable UUID hostId;
  private RoomStatusDto status;
  private int roundCount;
  private int maxPlayers;
  private int currentRound;
  private @Nullable UUID playlistId;
  private @Nullable RoomFilterPayload filter;
  private @Nullable ScheduledFuture<?> idleTimerHandle;

  public RoomSession(
      UUID roomId,
      @Nullable String code,
      RoomTypeDto roomType,
      @Nullable UUID hostId,
      RoomStatusDto status,
      int roundCount,
      int maxPlayers,
      @Nullable UUID playlistId,
      @Nullable RoomFilterPayload filter,
      Instant createdAt) {
    this.roomId = Objects.requireNonNull(roomId, "roomId must not be null");
    this.code = code;
    this.roomType = Objects.requireNonNull(roomType, "roomType must not be null");
    this.hostId = hostId;
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.roundCount = roundCount;
    this.maxPlayers = maxPlayers;
    this.currentRound = 0;
    this.playlistId = playlistId;
    this.filter = filter;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
  }

  public UUID roomId() {
    return roomId;
  }

  public @Nullable String code() {
    return code;
  }

  public RoomTypeDto roomType() {
    return roomType;
  }

  public @Nullable UUID hostId() {
    return hostId;
  }

  public void setHostId(@Nullable UUID hostId) {
    this.hostId = hostId;
  }

  public RoomStatusDto status() {
    return status;
  }

  public void setStatus(RoomStatusDto status) {
    this.status = Objects.requireNonNull(status, "status must not be null");
  }

  public int roundCount() {
    return roundCount;
  }

  public void setRoundCount(int roundCount) {
    this.roundCount = roundCount;
  }

  public int maxPlayers() {
    return maxPlayers;
  }

  public void setMaxPlayers(int maxPlayers) {
    this.maxPlayers = maxPlayers;
  }

  public int currentRound() {
    return currentRound;
  }

  public void setCurrentRound(int currentRound) {
    this.currentRound = currentRound;
  }

  public @Nullable UUID playlistId() {
    return playlistId;
  }

  public void setPlaylistId(@Nullable UUID playlistId) {
    this.playlistId = playlistId;
  }

  public @Nullable RoomFilterPayload filter() {
    return filter;
  }

  public void setFilter(@Nullable RoomFilterPayload filter) {
    this.filter = filter;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public List<RoomPlayer> players() {
    return Collections.unmodifiableList(players);
  }

  public List<RoomPlayer> waitingPlayers() {
    return Collections.unmodifiableList(waitingPlayers);
  }

  public Set<UUID> bannedUserIds() {
    return Collections.unmodifiableSet(bannedUserIds);
  }

  public boolean isQuickMatch() {
    return roomType == RoomTypeDto.QUICK_MATCH;
  }

  public boolean isPrivate() {
    return roomType == RoomTypeDto.PRIVATE_ROOM;
  }

  public boolean isPublic() {
    return roomType == RoomTypeDto.PUBLIC_ROOM;
  }

  public int playerCount() {
    return players.size() + waitingPlayers.size();
  }

  public boolean isFull() {
    return playerCount() >= maxPlayers;
  }

  public boolean isBanned(UUID userId) {
    return bannedUserIds.contains(userId);
  }

  public boolean hasMember(UUID userId) {
    return players.stream().anyMatch(p -> p.userId().equals(userId))
        || waitingPlayers.stream().anyMatch(p -> p.userId().equals(userId));
  }

  public Optional<RoomPlayer> findPlayer(UUID userId) {
    for (RoomPlayer player : players) {
      if (player.userId().equals(userId)) {
        return Optional.of(player);
      }
    }
    for (RoomPlayer player : waitingPlayers) {
      if (player.userId().equals(userId)) {
        return Optional.of(player);
      }
    }
    return Optional.empty();
  }

  public Set<UUID> allMemberIds() {
    Set<UUID> memberIds = new HashSet<>();
    for (RoomPlayer player : players) {
      memberIds.add(player.userId());
    }
    for (RoomPlayer player : waitingPlayers) {
      memberIds.add(player.userId());
    }
    return memberIds;
  }

  public void addPlayer(RoomPlayer player) {
    if (status == RoomStatusDto.LOBBY) {
      players.add(player);
    } else {
      waitingPlayers.add(player);
    }
    cancelIdleTimer();
  }

  /**
   * Xóa người chơi khỏi phòng (khi rời tự nguyện, bị mời ra, hoặc hết giờ mất kết nối).
   *
   * @param userId ID người chơi cần xóa
   * @return newHostId nếu người rời là chủ phòng và quyền chủ phòng được chuyển giao cho người vào
   *     sớm nhất; Optional.empty() nếu không đổi chủ phòng hoặc phòng không còn ai.
   */
  public Optional<UUID> removePlayer(UUID userId) {
    cancelDisconnectTimer(userId);
    boolean wasInPlayers = players.removeIf(p -> p.userId().equals(userId));
    waitingPlayers.removeIf(p -> p.userId().equals(userId));

    if (wasInPlayers && Objects.equals(hostId, userId)) {
      if (!players.isEmpty()) {
        RoomPlayer earliest = players.get(0);
        earliest.setHost(true);
        this.hostId = earliest.userId();
        return Optional.of(earliest.userId());
      } else {
        this.hostId = null;
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  public Optional<UUID> kickPlayer(UUID targetUserId) {
    bannedUserIds.add(targetUserId);
    return removePlayer(targetUserId);
  }

  public @Nullable ScheduledFuture<?> idleTimerHandle() {
    return idleTimerHandle;
  }

  public void setIdleTimerHandle(@Nullable ScheduledFuture<?> idleTimerHandle) {
    this.idleTimerHandle = idleTimerHandle;
  }

  public void cancelIdleTimer() {
    if (idleTimerHandle != null) {
      idleTimerHandle.cancel(false);
      idleTimerHandle = null;
    }
  }

  public void addDisconnectTimer(UUID userId, ScheduledFuture<?> timer) {
    ScheduledFuture<?> previous = disconnectTimers.put(userId, timer);
    if (previous != null) {
      previous.cancel(false);
    }
  }

  public void cancelDisconnectTimer(UUID userId) {
    ScheduledFuture<?> timer = disconnectTimers.remove(userId);
    if (timer != null) {
      timer.cancel(false);
    }
  }

  public RoomResponseDto toResponseDto() {
    RoomResponseDto dto = new RoomResponseDto();
    dto.setRoomId(roomId);
    dto.setCode(code);
    dto.setRoomType(roomType);
    dto.setHostId(hostId);
    dto.setStatus(status);
    dto.setRoundCount(RoomResponseDto.RoundCountEnum.fromValue(roundCount));
    dto.setMaxPlayers(maxPlayers);
    dto.setCurrentRound(currentRound);
    dto.setPlayerCount(playerCount());

    List<RoomPlayerSummaryDto> playerDtos = new ArrayList<>();
    for (RoomPlayer player : players) {
      playerDtos.add(player.toDto());
    }
    for (RoomPlayer player : waitingPlayers) {
      playerDtos.add(player.toDto());
    }
    dto.setPlayers(playerDtos);
    dto.setPlaylistId(playlistId);
    if (filter != null) {
      SoloFilterDto filterDto = new SoloFilterDto();
      filterDto.setGenreCodes(filter.genreCodes());
      filterDto.setCountryCodes(filter.countryCodes());
      filterDto.setDecades(filter.decades());
      filterDto.setArtistIds(filter.artistIds());
      dto.setFilter(filterDto);
    }
    dto.setCreatedAt(createdAt.atOffset(ZoneOffset.UTC));
    return dto;
  }
}
