package com.meloo.room.lobby;

import com.meloo.catalog.api.CatalogService;
import com.meloo.catalog.api.PlaylistInfo;
import com.meloo.catalog.api.TrackFilter;
import com.meloo.catalog.api.TrackInfo;
import com.meloo.generated.model.CreateRoomRequestDto;
import com.meloo.generated.model.PublicRoomItemDto;
import com.meloo.generated.model.PublicRoomPageDto;
import com.meloo.generated.model.QuickMatchRequestDto;
import com.meloo.generated.model.RoomResponseDto;
import com.meloo.generated.model.RoomStatusDto;
import com.meloo.generated.model.RoomTypeDto;
import com.meloo.generated.model.SoloFilterDto;
import com.meloo.room.api.RoomDescriptor;
import com.meloo.room.api.message.ErrorMessage;
import com.meloo.room.api.message.KickCommand;
import com.meloo.room.api.message.PlayerConnectionChangedEvent;
import com.meloo.room.api.message.PlayerJoinedEvent;
import com.meloo.room.api.message.PlayerKickedEvent;
import com.meloo.room.api.message.PlayerLeftEvent;
import com.meloo.room.api.message.PlayerReadyChangedEvent;
import com.meloo.room.api.message.ReadyCommand;
import com.meloo.room.api.message.RoomFilterPayload;
import com.meloo.room.api.message.RoomPlayerSummaryPayload;
import com.meloo.room.api.message.RoomSettingsChangedEvent;
import com.meloo.room.api.message.SettingsCommand;
import com.meloo.room.api.message.WaitingListChangedEvent;
import com.meloo.room.matchmaking.QuickMatchLobbyChanged;
import com.meloo.room.realtime.RoomActor;
import com.meloo.room.realtime.RoomConnectionHandler;
import com.meloo.room.realtime.RoomEventPublisher;
import com.meloo.room.realtime.RoomEventPublisher.PrivateQueue;
import com.meloo.room.realtime.RoomEventPublisher.RoomTopic;
import com.meloo.room.realtime.RoomRegistry;
import com.meloo.shared.api.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * Điều phối vòng đời phòng và các hành vi trong sảnh chờ (BR-11..BR-15).
 *
 * <p>Mọi thay đổi trạng thái đều đi qua hàng đợi lệnh tuần tự của {@link RoomActor} cho mỗi phòng.
 */
@Service
public class RoomLobbyService {

  private org.springframework.context.ApplicationEventPublisher matchLifecyclePublisher =
      event -> {};

  @org.springframework.beans.factory.annotation.Autowired
  void setMatchLifecyclePublisher(org.springframework.context.ApplicationEventPublisher publisher) {
    this.matchLifecyclePublisher = publisher;
  }

  private void notifyMatch(
      UUID roomId, UUID userId, com.meloo.room.match.RoomMembershipChanged.Change change) {
    matchLifecyclePublisher.publishEvent(
        new com.meloo.room.match.RoomMembershipChanged(roomId, userId, change));
  }

  private final RoomRegistry roomRegistry;
  private final RoomEventPublisher eventPublisher;
  private final CatalogService catalogService;
  private final RoomCodeGenerator codeGenerator;
  private final RoomLobbyProperties properties;
  private final Clock clock;
  private final @Nullable SimpUserRegistry simpUserRegistry;
  private final @Nullable MessageChannel clientInboundChannel;

  private final ConcurrentMap<UUID, RoomSession> roomsById = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, UUID> roomsByCode = new ConcurrentHashMap<>();
  private final ReentrantLock quickMatchLock = new ReentrantLock();

  @org.springframework.beans.factory.annotation.Autowired
  public RoomLobbyService(
      RoomRegistry roomRegistry,
      RoomEventPublisher eventPublisher,
      CatalogService catalogService,
      RoomCodeGenerator codeGenerator,
      RoomLobbyProperties properties,
      @org.springframework.beans.factory.annotation.Autowired(required = false)
          @Nullable SimpUserRegistry simpUserRegistry,
      @org.springframework.beans.factory.annotation.Autowired(required = false)
          @org.springframework.beans.factory.annotation.Qualifier("clientInboundChannel") @Nullable MessageChannel clientInboundChannel) {
    this(
        roomRegistry,
        eventPublisher,
        catalogService,
        codeGenerator,
        properties,
        Clock.systemUTC(),
        simpUserRegistry,
        clientInboundChannel);
  }

  RoomLobbyService(
      RoomRegistry roomRegistry,
      RoomEventPublisher eventPublisher,
      CatalogService catalogService,
      RoomCodeGenerator codeGenerator,
      RoomLobbyProperties properties) {
    this(
        roomRegistry,
        eventPublisher,
        catalogService,
        codeGenerator,
        properties,
        Clock.systemUTC(),
        null,
        null);
  }

  RoomLobbyService(
      RoomRegistry roomRegistry,
      RoomEventPublisher eventPublisher,
      CatalogService catalogService,
      RoomCodeGenerator codeGenerator,
      RoomLobbyProperties properties,
      Clock clock) {
    this(
        roomRegistry, eventPublisher, catalogService, codeGenerator, properties, clock, null, null);
  }

  RoomLobbyService(
      RoomRegistry roomRegistry,
      RoomEventPublisher eventPublisher,
      CatalogService catalogService,
      RoomCodeGenerator codeGenerator,
      RoomLobbyProperties properties,
      Clock clock,
      @Nullable SimpUserRegistry simpUserRegistry,
      @Nullable MessageChannel clientInboundChannel) {
    this.roomRegistry = Objects.requireNonNull(roomRegistry, "roomRegistry must not be null");
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    this.catalogService = Objects.requireNonNull(catalogService, "catalogService must not be null");
    this.codeGenerator = Objects.requireNonNull(codeGenerator, "codeGenerator must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.simpUserRegistry = simpUserRegistry;
    this.clientInboundChannel = clientInboundChannel;
  }

  /**
   * Tạo phòng chơi mới (riêng hoặc công khai, BR-11, BR-12).
   *
   * @param creatorId ID người tạo (trở thành chủ phòng)
   * @param creatorNickname Biệt danh người tạo
   * @param avatarUrl Avatar người tạo nếu có
   * @param isGuest Người tạo có phải tài khoản khách không
   * @param request Thông tin cài đặt tạo phòng
   * @return Trạng thái ban đầu của phòng
   */
  public RoomResponseDto createRoom(
      UUID creatorId,
      String creatorNickname,
      @Nullable String avatarUrl,
      boolean isGuest,
      CreateRoomRequestDto request) {
    return createRoom(creatorId, creatorNickname, avatarUrl, null, isGuest, request);
  }

  public RoomResponseDto createRoom(
      UUID creatorId,
      String creatorNickname,
      @Nullable String avatarUrl,
      @Nullable String avatarId,
      boolean isGuest,
      CreateRoomRequestDto request) {
    Instant now = clock.instant();

    if (request.getPlaylistId() != null && request.getFilter() != null) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_FAILED",
          "playlistId and filter are mutually exclusive");
    }

    int roundCount =
        request.getRoundCount() != null
            ? request.getRoundCount().getValue()
            : properties.defaultRoundCount();
    if (roundCount != 5 && roundCount != 10 && roundCount != 15) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "roundCount must be 5, 10, or 15");
    }

    int maxPlayers =
        request.getMaxPlayers() != null ? request.getMaxPlayers() : properties.maxPlayers();
    if (maxPlayers < properties.minPlayers() || maxPlayers > properties.maxPlayers()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_FAILED",
          "maxPlayers must be between "
              + properties.minPlayers()
              + " and "
              + properties.maxPlayers());
    }

    UUID playlistId = request.getPlaylistId();
    SoloFilterDto soloFilter = request.getFilter();
    RoomFilterPayload filterPayload = toFilterPayload(soloFilter);

    validateTracks(playlistId, filterPayload, roundCount);

    if (request.getRoomType() == null) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "roomType must not be null");
    }
    RoomTypeDto roomType;
    try {
      roomType = RoomTypeDto.fromValue(request.getRoomType().getValue());
    } catch (IllegalArgumentException e) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_FAILED",
          "Invalid roomType: " + request.getRoomType());
    }

    String code = codeGenerator.generateUniqueCode(c -> !roomsByCode.containsKey(c));
    UUID roomId = UUID.randomUUID();

    RoomSession session =
        new RoomSession(
            roomId,
            code,
            roomType,
            creatorId,
            RoomStatusDto.LOBBY,
            roundCount,
            maxPlayers,
            playlistId,
            filterPayload,
            now);

    RoomPlayer host =
        new RoomPlayer(
            creatorId, creatorNickname, avatarUrl, avatarId, true, true, isGuest, false, 0, now);
    session.addPlayer(host);

    roomsById.put(roomId, session);
    roomsByCode.put(code, roomId);

    RoomDescriptor descriptor = new RoomDescriptor(roomId, Set.of(creatorId), session.isPrivate());
    RoomConnectionHandler handler = createConnectionHandler(roomId, session);
    roomRegistry.register(descriptor, handler);

    return session.toResponseDto();
  }

  /** Tạo phòng ghép trận nhanh (BR-12, BR-13, không có chủ phòng, không có code). */
  public RoomResponseDto createQuickMatchRoom(
      @Nullable UUID playlistId,
      @Nullable RoomFilterPayload filter,
      @Nullable Integer roundCount,
      @Nullable Integer maxPlayers) {
    Instant now = clock.instant();
    int rc = roundCount != null ? roundCount : properties.defaultRoundCount();
    int mp = maxPlayers != null ? maxPlayers : properties.maxPlayers();
    UUID roomId = UUID.randomUUID();

    RoomSession session =
        new RoomSession(
            roomId,
            null,
            RoomTypeDto.QUICK_MATCH,
            null,
            RoomStatusDto.LOBBY,
            rc,
            mp,
            playlistId,
            filter,
            now);

    roomsById.put(roomId, session);
    RoomDescriptor descriptor = new RoomDescriptor(roomId, Set.of(), false);
    RoomConnectionHandler handler = createConnectionHandler(roomId, session);
    roomRegistry.register(descriptor, handler);
    return session.toResponseDto();
  }

  /**
   * Ghép trận nhanh hoặc tạo phòng ghép trận mới (BR-13, OpenAPI /api/v1/rooms/quick-match).
   *
   * @param userId ID người chơi gọi API
   * @param nickname Biệt danh
   * @param avatarUrl Avatar nếu có
   * @param isGuest Người chơi có phải tài khoản khách không
   * @param request Tuỳ chọn ghép trận (playlistId hoặc filter)
   * @return Phòng ghép trận đã tham gia hoặc vừa tạo
   */
  public RoomResponseDto quickMatch(
      UUID userId,
      String nickname,
      @Nullable String avatarUrl,
      boolean isGuest,
      @Nullable QuickMatchRequestDto request) {
    return quickMatch(userId, nickname, avatarUrl, null, isGuest, request);
  }

  public RoomResponseDto quickMatch(
      UUID userId,
      String nickname,
      @Nullable String avatarUrl,
      @Nullable String avatarId,
      boolean isGuest,
      @Nullable QuickMatchRequestDto request) {
    Instant now = clock.instant();

    UUID playlistId = null;
    RoomFilterPayload filterPayload = null;
    if (request != null) {
      boolean hasPlaylist = request.getPlaylistId() != null;
      boolean hasFilter =
          request.getFilter() != null
              || (request.getGenreCodes() != null && !request.getGenreCodes().isEmpty())
              || (request.getDecades() != null && !request.getDecades().isEmpty());
      if (hasPlaylist && hasFilter) {
        throw new ApiException(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_FAILED",
            "playlistId and filter are mutually exclusive");
      }
      playlistId = request.getPlaylistId();
      if (request.getFilter() != null) {
        filterPayload = toFilterPayload(request.getFilter());
      } else if ((request.getGenreCodes() != null && !request.getGenreCodes().isEmpty())
          || (request.getDecades() != null && !request.getDecades().isEmpty())) {
        filterPayload =
            new RoomFilterPayload(request.getGenreCodes(), null, request.getDecades(), null);
      }
    }

    int roundCount = properties.defaultRoundCount();
    validateTracks(playlistId, filterPayload, roundCount);

    quickMatchLock.lock();
    try {
      UUID requestedPlaylist = playlistId;
      RoomFilterPayload requestedFilter = filterPayload;
      for (RoomSession session : roomsById.values()) {
        QuickMatchJoin joined =
            submitIfOpen(
                session.roomId(),
                () -> {
                  if (!session.isQuickMatch()
                      || session.status() != RoomStatusDto.LOBBY
                      || session.isBanned(userId)
                      || !Objects.equals(session.playlistId(), requestedPlaylist)
                      || !matchesFilter(session.filter(), requestedFilter)) return null;
                  if (session.hasMember(userId)) {
                    return new QuickMatchJoin(
                        session.toResponseDto(), CompletableFuture.completedFuture(null));
                  }
                  if (session.isFull()) return null;
                  return addQuickMatchPlayer(
                      session, userId, nickname, avatarUrl, avatarId, isGuest, now);
                });
        if (joined != null) {
          await(joined.updated());
          return joined.response();
        }
      }
      RoomResponseDto created = createQuickMatchRoom(playlistId, filterPayload, roundCount, null);
      RoomSession session = roomsById.get(created.getRoomId());
      QuickMatchJoin joined =
          await(
              roomRegistry
                  .actor(created.getRoomId())
                  .orElseThrow()
                  .submit(
                      () ->
                          addQuickMatchPlayer(
                              session, userId, nickname, avatarUrl, avatarId, isGuest, now)));
      await(joined.updated());
      return joined.response();
    } finally {
      quickMatchLock.unlock();
    }
  }

  private record QuickMatchJoin(RoomResponseDto response, CompletableFuture<Void> updated) {}

  private QuickMatchJoin addQuickMatchPlayer(
      RoomSession session,
      UUID userId,
      String nickname,
      @Nullable String avatarUrl,
      @Nullable String avatarId,
      boolean guest,
      Instant now) {
    RoomPlayer player =
        new RoomPlayer(userId, nickname, avatarUrl, avatarId, false, true, guest, true, 0, now);
    session.addPlayer(player);
    CompletableFuture<Void> updated =
        roomRegistry.update(new RoomDescriptor(session.roomId(), session.allMemberIds(), false));
    eventPublisher.publish(
        session.roomId(), RoomTopic.LOBBY, new PlayerJoinedEvent(player.toPayload(), now));
    matchLifecyclePublisher.publishEvent(new QuickMatchLobbyChanged(session.roomId(), false));
    return new QuickMatchJoin(session.toResponseDto(), updated);
  }

  private static boolean matchesFilter(
      @Nullable RoomFilterPayload a, @Nullable RoomFilterPayload b) {
    if (a == null) a = new RoomFilterPayload(null, null, null, null);
    if (b == null) b = new RoomFilterPayload(null, null, null, null);
    return sameValues(a.genreCodes(), b.genreCodes())
        && sameValues(a.countryCodes(), b.countryCodes())
        && sameValues(a.decades(), b.decades())
        && sameValues(a.artistIds(), b.artistIds());
  }

  private static <T> boolean sameValues(@Nullable List<T> a, @Nullable List<T> b) {
    return (a == null ? Set.of() : Set.copyOf(a)).equals(b == null ? Set.of() : Set.copyOf(b));
  }

  /** Vào phòng bằng mã 6 ký tự hoặc đường dẫn liên kết (BR-11, BR-12, BR-13, BR-15). */
  public RoomResponseDto joinRoomByCode(
      UUID userId, String nickname, @Nullable String avatarUrl, boolean isGuest, String code) {
    return joinRoomByCode(userId, nickname, avatarUrl, null, isGuest, code);
  }

  public RoomResponseDto joinRoomByCode(
      UUID userId,
      String nickname,
      @Nullable String avatarUrl,
      @Nullable String avatarId,
      boolean isGuest,
      String code) {
    if (code == null || code.isBlank()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Mã phòng không được để trống");
    }

    String normalizedCode = code.trim().toUpperCase();
    UUID roomId = roomsByCode.get(normalizedCode);
    if (roomId == null) {
      throw new ApiException(
          HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Room not found or already closed");
    }

    RoomSession session = roomsById.get(roomId);
    if (session == null || session.status() == RoomStatusDto.CLOSED) {
      throw new ApiException(
          HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Room not found or already closed");
    }

    if (session.isQuickMatch()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "QUICK_MATCH_CANNOT_JOIN",
          "Phòng loại QUICK_MATCH không vào được bằng mã hay link (BR-13)");
    }

    RoomActor actor =
        roomRegistry
            .actor(roomId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ROOM_NOT_FOUND",
                        "Phòng không tồn tại hoặc đã đóng"));

    JoinResult joinResult =
        await(
            actor.submit(
                () -> doJoin(session, actor, userId, nickname, avatarUrl, avatarId, isGuest)));

    if (joinResult.updateFuture() != null) {
      await(joinResult.updateFuture());
    }

    if (joinResult.player() != null) {
      Instant now = clock.instant();
      if (session.status() == RoomStatusDto.LOBBY) {
        eventPublisher.publish(
            session.roomId(),
            RoomTopic.LOBBY,
            new PlayerJoinedEvent(joinResult.player().toPayload(), now));
      } else if (session.status() == RoomStatusDto.PLAYING) {
        List<RoomPlayerSummaryPayload> waitingPayloads =
            session.waitingPlayers().stream().map(RoomPlayer::toPayload).toList();
        eventPublisher.publish(
            session.roomId(), RoomTopic.LOBBY, new WaitingListChangedEvent(waitingPayloads, now));
      }
    }

    return session.toResponseDto();
  }

  private record JoinResult(
      @Nullable RoomPlayer player, @Nullable CompletableFuture<Void> updateFuture) {}

  private JoinResult doJoin(
      RoomSession session,
      RoomActor actor,
      UUID userId,
      String nickname,
      @Nullable String avatarUrl,
      @Nullable String avatarId,
      boolean isGuest) {
    Instant now = clock.instant();

    if (session.isBanned(userId)) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "ROOM_KICKED", "Người chơi đã bị mời ra khỏi phòng này (BR-12)");
    }

    if (session.status() == RoomStatusDto.CLOSED) {
      throw new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Phòng đã đóng");
    }

    Optional<RoomPlayer> existingOpt = session.findPlayer(userId);
    if (existingOpt.isPresent()) {
      return new JoinResult(null, null);
    }

    if (session.isFull()) {
      throw new ApiException(
          HttpStatus.CONFLICT, "ROOM_FULL", "Phòng đã đủ số người tối đa (BR-11, BR-15)");
    }

    RoomPlayer player =
        new RoomPlayer(userId, nickname, avatarUrl, avatarId, false, false, isGuest, false, 0, now);
    session.addPlayer(player);

    RoomDescriptor descriptor =
        new RoomDescriptor(session.roomId(), session.allMemberIds(), session.isPrivate());
    CompletableFuture<Void> updateFuture = roomRegistry.update(descriptor);

    return new JoinResult(player, updateFuture);
  }

  /** Xử lý lệnh sẵn sàng / hủy sẵn sàng (/app/rooms/{roomId}/ready). */
  public void handleReady(UUID roomId, UUID userId, ReadyCommand command) {
    RoomSession session = roomsById.get(roomId);
    if (session == null) return;
    RoomActor actor = roomRegistry.actor(roomId).orElse(null);
    if (actor == null) return;

    actor.execute(
        () -> {
          Instant now = clock.instant();
          if (session.isQuickMatch()) {
            eventPublisher.sendPrivate(
                userId,
                PrivateQueue.ERRORS,
                new ErrorMessage(
                    "INVALID_COMMAND",
                    "Phòng ghép trận không hỗ trợ sẵn sàng (BR-12, BR-13)",
                    null,
                    now));
            return;
          }

          Optional<RoomPlayer> playerOpt = session.findPlayer(userId);
          if (playerOpt.isEmpty()) return;

          RoomPlayer player = playerOpt.get();
          player.setReady(command.ready());
          eventPublisher.publish(
              roomId, RoomTopic.LOBBY, new PlayerReadyChangedEvent(userId, command.ready(), now));
        });
  }

  /** Xử lý lệnh cập nhật cài đặt phòng (/app/rooms/{roomId}/settings). */
  public void handleSettings(UUID roomId, UUID userId, SettingsCommand command) {
    RoomSession session = roomsById.get(roomId);
    if (session == null) return;
    RoomActor actor = roomRegistry.actor(roomId).orElse(null);
    if (actor == null) return;

    actor.execute(
        () -> {
          Instant now = clock.instant();
          if (session.isQuickMatch()) {
            eventPublisher.sendPrivate(
                userId,
                PrivateQueue.ERRORS,
                new ErrorMessage(
                    "INVALID_COMMAND",
                    "Phòng ghép trận không hỗ trợ chỉnh cài đặt (BR-12, BR-13)",
                    null,
                    now));
            return;
          }

          if (!Objects.equals(session.hostId(), userId)) {
            eventPublisher.sendPrivate(
                userId,
                PrivateQueue.ERRORS,
                new ErrorMessage(
                    "FORBIDDEN", "Chỉ chủ phòng mới có quyền chỉnh cài đặt (BR-12)", null, now));
            return;
          }

          if (session.status() != RoomStatusDto.LOBBY) {
            eventPublisher.sendPrivate(
                userId,
                PrivateQueue.ERRORS,
                new ErrorMessage(
                    "INVALID_STATE", "Không thể chỉnh cài đặt khi trận đang diễn ra", null, now));
            return;
          }

          if (command.maxPlayers() != null) {
            int mp = command.maxPlayers();
            if (mp < properties.minPlayers()
                || mp > properties.maxPlayers()
                || mp < session.playerCount()) {
              eventPublisher.sendPrivate(
                  userId,
                  PrivateQueue.ERRORS,
                  new ErrorMessage(
                      "VALIDATION_FAILED",
                      "Số người tối đa không hợp lệ (phải từ 2-5 và không nhỏ hơn số người hiện tại)",
                      null,
                      now));
              return;
            }
          }

          if (command.roundCount() != null) {
            int rc = command.roundCount();
            if (rc != 5 && rc != 10 && rc != 15) {
              eventPublisher.sendPrivate(
                  userId,
                  PrivateQueue.ERRORS,
                  new ErrorMessage(
                      "VALIDATION_FAILED", "Số vòng phải là 5, 10 hoặc 15 (BR-09)", null, now));
              return;
            }
          }

          if (command.playlistId() != null && command.filter() != null) {
            eventPublisher.sendPrivate(
                userId,
                PrivateQueue.ERRORS,
                new ErrorMessage(
                    "VALIDATION_FAILED", "Không thể chọn cả bộ đề và bộ lọc", null, now));
            return;
          }

          int targetRoundCount =
              command.roundCount() != null ? command.roundCount() : session.roundCount();

          if (command.playlistId() != null) {
            var playlistOpt =
                catalogService.findPlaylistById(command.playlistId()).filter(PlaylistInfo::enabled);
            if (playlistOpt.isEmpty()) {
              eventPublisher.sendPrivate(
                  userId,
                  PrivateQueue.ERRORS,
                  new ErrorMessage(
                      "PLAYLIST_NOT_FOUND",
                      "Bộ nhạc không tồn tại hoặc đã bị vô hiệu hóa",
                      null,
                      now));
              return;
            }
            List<TrackInfo> candidate =
                catalogService.selectRandomTracksByPlaylist(
                    command.playlistId(), targetRoundCount, Set.of());
            if (candidate.size() < targetRoundCount) {
              int missing = targetRoundCount - candidate.size();
              eventPublisher.sendPrivate(
                  userId,
                  PrivateQueue.ERRORS,
                  new ErrorMessage(
                      "NOT_ENOUGH_TRACKS",
                      "Bộ nhạc không đủ bài hợp lệ cho số vòng đã chọn (BR-09)",
                      null,
                      "NOT_ENOUGH_TRACKS",
                      missing,
                      now));
              return;
            }
          } else if (command.filter() != null) {
            TrackFilter tf = toTrackFilter(command.filter());
            List<TrackInfo> candidate =
                catalogService.selectRandomTracks(tf, targetRoundCount, Set.of());
            if (candidate.size() < targetRoundCount) {
              int missing = targetRoundCount - candidate.size();
              eventPublisher.sendPrivate(
                  userId,
                  PrivateQueue.ERRORS,
                  new ErrorMessage(
                      "NOT_ENOUGH_TRACKS",
                      "Bộ lọc không đủ bài hợp lệ cho số vòng đã chọn (BR-09)",
                      null,
                      "NOT_ENOUGH_TRACKS",
                      missing,
                      now));
              return;
            }
          }

          if (command.roundCount() != null) {
            session.setRoundCount(command.roundCount());
          }
          if (command.maxPlayers() != null) {
            session.setMaxPlayers(command.maxPlayers());
          }
          if (command.playlistId() != null) {
            session.setPlaylistId(command.playlistId());
            session.setFilter(null);
          }
          if (command.filter() != null) {
            session.setFilter(command.filter());
            session.setPlaylistId(null);
          }

          eventPublisher.publish(
              roomId,
              RoomTopic.LOBBY,
              new RoomSettingsChangedEvent(
                  session.roundCount(),
                  session.maxPlayers(),
                  session.playlistId(),
                  session.filter(),
                  now));
        });
  }

  /** Xử lý lệnh mời người chơi ra khỏi phòng (/app/rooms/{roomId}/kick). */
  public void handleKick(UUID roomId, UUID userId, KickCommand command) {
    RoomSession session = roomsById.get(roomId);
    if (session == null) return;
    RoomActor actor = roomRegistry.actor(roomId).orElse(null);
    if (actor == null) return;

    actor.execute(
        () -> {
          Instant now = clock.instant();
          if (session.isQuickMatch()) {
            eventPublisher.sendPrivate(
                userId,
                PrivateQueue.ERRORS,
                new ErrorMessage(
                    "INVALID_COMMAND",
                    "Phòng ghép trận không hỗ trợ mời người ra (BR-12, BR-13)",
                    null,
                    now));
            return;
          }

          if (!Objects.equals(session.hostId(), userId)) {
            eventPublisher.sendPrivate(
                userId,
                PrivateQueue.ERRORS,
                new ErrorMessage(
                    "FORBIDDEN", "Chỉ chủ phòng mới có quyền mời người ra (BR-12)", null, now));
            return;
          }

          UUID targetUserId = command.targetUserId();
          if (targetUserId == null || targetUserId.equals(userId)) {
            eventPublisher.sendPrivate(
                userId,
                PrivateQueue.ERRORS,
                new ErrorMessage("INVALID_COMMAND", "Không thể tự mời chính mình ra", null, now));
            return;
          }

          if (!session.hasMember(targetUserId)) {
            eventPublisher.sendPrivate(
                userId,
                PrivateQueue.ERRORS,
                new ErrorMessage("PLAYER_NOT_FOUND", "Người chơi không có trong phòng", null, now));
            return;
          }

          session.kickPlayer(targetUserId);
          notifyMatch(roomId, targetUserId, com.meloo.room.match.RoomMembershipChanged.Change.LEFT);
          roomRegistry.update(
              new RoomDescriptor(roomId, session.allMemberIds(), session.isPrivate()));
          eventPublisher.publish(roomId, RoomTopic.LOBBY, new PlayerKickedEvent(targetUserId, now));
          revokeRoomSubscriptions(targetUserId, roomId);
        });
  }

  private void revokeRoomSubscriptions(UUID userId, UUID roomId) {
    if (simpUserRegistry != null && clientInboundChannel != null) {
      SimpUser simpUser = simpUserRegistry.getUser(userId.toString());
      if (simpUser != null) {
        for (SimpSession session : simpUser.getSessions()) {
          for (SimpSubscription sub : session.getSubscriptions()) {
            if (sub.getDestination().startsWith("/topic/rooms/" + roomId)) {
              StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
              accessor.setSessionId(session.getId());
              accessor.setSubscriptionId(sub.getId());
              accessor.setDestination(sub.getDestination());
              accessor.setUser(() -> userId.toString());
              accessor.setLeaveMutable(true);
              clientInboundChannel.send(
                  MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()));
            }
          }
        }
      }
    }
  }

  /** Xử lý lệnh tự nguyện rời phòng (/app/rooms/{roomId}/leave). */
  public void handleLeave(UUID roomId, UUID userId) {
    RoomSession session = roomsById.get(roomId);
    if (session == null) return;
    RoomActor actor = roomRegistry.actor(roomId).orElse(null);
    if (actor == null) return;

    actor.execute(
        () -> {
          if (!session.hasMember(userId)) return;
          Instant now = clock.instant();
          Optional<UUID> newHostIdOpt = session.removePlayer(userId);
          notifyMatch(roomId, userId, com.meloo.room.match.RoomMembershipChanged.Change.LEFT);
          roomRegistry.update(
              new RoomDescriptor(roomId, session.allMemberIds(), session.isPrivate()));

          eventPublisher.publish(
              roomId,
              RoomTopic.LOBBY,
              new PlayerLeftEvent(userId, newHostIdOpt.orElse(null), "VOLUNTARY", now));

          if (session.playerCount() == 0) {
            scheduleEmptyRoomClose(roomId, session, actor);
          }
        });
  }

  /** Trả về danh sách phòng công khai phân trang (BR-11, BR-15). */
  public PublicRoomPageDto listPublicRooms(int page, int size) {
    List<PublicListing> allItems =
        roomsById.values().stream()
            .filter(RoomSession::isPublic)
            .map(
                room ->
                    submitIfOpen(
                        room.roomId(),
                        () ->
                            room.status() == RoomStatusDto.LOBBY && !room.isFull()
                                ? new PublicListing(toPublicItemDto(room), room.playlistId())
                                : null))
            .filter(Objects::nonNull)
            .sorted((a, b) -> b.item().getCreatedAt().compareTo(a.item().getCreatedAt()))
            .toList();

    int totalItems = allItems.size();
    int totalPages = size > 0 ? (int) Math.ceil((double) totalItems / size) : 0;
    int fromIndex = Math.min(page * size, totalItems);
    int toIndex = Math.min(fromIndex + size, totalItems);
    List<PublicRoomItemDto> pagedItems =
        allItems.subList(fromIndex, toIndex).stream()
            .map(
                snapshot -> {
                  PublicRoomItemDto item = snapshot.item();
                  if (snapshot.playlistId() != null) {
                    item.setPlaylistName(
                        catalogService
                            .findPlaylistById(snapshot.playlistId())
                            .map(PlaylistInfo::name)
                            .orElse(null));
                  }
                  return item;
                })
            .toList();

    PublicRoomPageDto pageDto = new PublicRoomPageDto();
    pageDto.setPage(page);
    pageDto.setSize(size);
    pageDto.setTotalItems((long) totalItems);
    pageDto.setTotalPages(totalPages);
    pageDto.setItems(pagedItems);
    return pageDto;
  }

  public Optional<RoomSession> getRoom(UUID roomId) {
    return Optional.ofNullable(roomsById.get(roomId));
  }

  public Optional<RoomSession> getRoomByCode(String code) {
    if (code == null) return Optional.empty();
    UUID roomId = roomsByCode.get(code.trim().toUpperCase());
    return roomId != null ? Optional.ofNullable(roomsById.get(roomId)) : Optional.empty();
  }

  private RoomConnectionHandler createConnectionHandler(UUID roomId, RoomSession session) {
    return new RoomConnectionHandler() {
      @Override
      public void connected(UUID userId) {
        RoomActor actor = roomRegistry.actor(roomId).orElse(null);
        if (actor == null) return;
        actor.execute(
            () -> {
              Instant now = clock.instant();
              session.cancelDisconnectTimer(userId);
              Optional<RoomPlayer> playerOpt = session.findPlayer(userId);
              if (playerOpt.isPresent()) {
                RoomPlayer player = playerOpt.get();
                player.setConnected(true);
                notifyMatch(
                    roomId, userId, com.meloo.room.match.RoomMembershipChanged.Change.CONNECTED);
                eventPublisher.publish(
                    roomId, RoomTopic.LOBBY, new PlayerConnectionChangedEvent(userId, true, now));
              }
            });
      }

      @Override
      public void disconnected(UUID userId) {
        RoomActor actor = roomRegistry.actor(roomId).orElse(null);
        if (actor == null) return;
        actor.execute(
            () -> {
              Instant now = clock.instant();
              Optional<RoomPlayer> playerOpt = session.findPlayer(userId);
              if (playerOpt.isPresent()) {
                RoomPlayer player = playerOpt.get();
                player.setConnected(false);
                notifyMatch(
                    roomId, userId, com.meloo.room.match.RoomMembershipChanged.Change.DISCONNECTED);
                eventPublisher.publish(
                    roomId, RoomTopic.LOBBY, new PlayerConnectionChangedEvent(userId, false, now));

                ScheduledFuture<?> timer =
                    actor.schedule(
                        properties.disconnectTimeout(),
                        () -> handleDisconnectTimeout(roomId, session, actor, userId));
                session.addDisconnectTimer(userId, timer);
              }
            });
      }
    };
  }

  private void handleDisconnectTimeout(
      UUID roomId, RoomSession session, RoomActor actor, UUID userId) {
    Optional<RoomPlayer> playerOpt = session.findPlayer(userId);
    if (playerOpt.isPresent() && !playerOpt.get().isConnected()) {
      Instant now = clock.instant();
      Optional<UUID> newHostId = session.removePlayer(userId);
      notifyMatch(roomId, userId, com.meloo.room.match.RoomMembershipChanged.Change.LEFT);
      roomRegistry.update(new RoomDescriptor(roomId, session.allMemberIds(), session.isPrivate()));
      eventPublisher.publish(
          roomId,
          RoomTopic.LOBBY,
          new PlayerLeftEvent(userId, newHostId.orElse(null), "DISCONNECTED", now));

      if (session.playerCount() == 0) {
        scheduleEmptyRoomClose(roomId, session, actor);
      }
    }
  }

  private void scheduleEmptyRoomClose(UUID roomId, RoomSession session, RoomActor actor) {
    ScheduledFuture<?> timer =
        actor.schedule(
            properties.idleTimeout(),
            () -> {
              if (session.playerCount() == 0) {
                session.setStatus(RoomStatusDto.CLOSED);
                if (session.code() != null) {
                  roomsByCode.remove(session.code());
                }
                roomsById.remove(roomId);
                roomRegistry.remove(roomId);
              }
            });
    session.setIdleTimerHandle(timer);
  }

  private void validateTracks(
      @Nullable UUID playlistId, @Nullable RoomFilterPayload filter, int roundCount) {
    if (playlistId != null) {
      catalogService
          .findPlaylistById(playlistId)
          .filter(PlaylistInfo::enabled)
          .orElseThrow(
              () ->
                  new ApiException(
                      HttpStatus.NOT_FOUND,
                      "PLAYLIST_NOT_FOUND",
                      "The playlist was not found or has been disabled."));
      List<TrackInfo> candidateTracks =
          catalogService.selectRandomTracksByPlaylist(playlistId, roundCount, Set.of());
      if (candidateTracks.size() < roundCount) {
        throw new RoomNotEnoughTracksException(roundCount - candidateTracks.size());
      }
    } else {
      TrackFilter trackFilter = filter != null ? toTrackFilter(filter) : null;
      List<TrackInfo> candidateTracks =
          catalogService.selectRandomTracks(
              trackFilter != null && !trackFilter.isEmpty() ? trackFilter : null,
              roundCount,
              Set.of());
      if (candidateTracks.size() < roundCount) {
        throw new RoomNotEnoughTracksException(roundCount - candidateTracks.size());
      }
    }
  }

  private record PublicListing(PublicRoomItemDto item, @Nullable UUID playlistId) {}

  private PublicRoomItemDto toPublicItemDto(RoomSession room) {
    String hostNickname =
        room.hostId() != null
            ? room.findPlayer(room.hostId()).map(RoomPlayer::nickname).orElse("Host")
            : "System";

    PublicRoomItemDto item =
        new PublicRoomItemDto(
            room.roomId(),
            room.code() != null ? room.code() : "",
            hostNickname,
            room.playerCount(),
            room.maxPlayers(),
            PublicRoomItemDto.RoundCountEnum.fromValue(room.roundCount()),
            room.status(),
            room.createdAt().atOffset(ZoneOffset.UTC));
    return item;
  }

  private @Nullable RoomFilterPayload toFilterPayload(@Nullable SoloFilterDto filterDto) {
    if (filterDto == null) return null;
    return new RoomFilterPayload(
        filterDto.getGenreCodes(),
        filterDto.getCountryCodes(),
        filterDto.getDecades(),
        filterDto.getArtistIds());
  }

  private TrackFilter toTrackFilter(RoomFilterPayload payload) {
    return new TrackFilter(
        payload.genreCodes(), payload.countryCodes(), payload.decades(), payload.artistIds());
  }

  private <T> @Nullable T submitIfOpen(UUID roomId, java.util.function.Supplier<T> command) {
    RoomActor actor = roomRegistry.actor(roomId).orElse(null);
    if (actor == null) return null;
    try {
      return await(actor.submit(command));
    } catch (IllegalStateException failure) {
      // An idle room can close after lookup but before this command reaches its actor.
      if (roomRegistry.actor(roomId).isEmpty()) return null;
      throw failure;
    }
  }

  private static <T> T await(CompletableFuture<T> future) {
    try {
      return future.join();
    } catch (CompletionException failure) {
      if (failure.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw failure;
    }
  }
}
