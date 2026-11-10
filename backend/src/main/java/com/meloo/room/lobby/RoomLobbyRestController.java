package com.meloo.room.lobby;

import com.meloo.generated.api.RoomApi;
import com.meloo.generated.model.CreateRoomRequestDto;
import com.meloo.generated.model.JoinRoomRequestDto;
import com.meloo.generated.model.PublicRoomPageDto;
import com.meloo.generated.model.QuickMatchRequestDto;
import com.meloo.generated.model.RoomResponseDto;
import com.meloo.room.matchmaking.MatchmakingService;
import com.meloo.shared.api.ApiException;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

/** Controller triển khai API REST cho phòng nhiều người chơi theo hợp đồng OpenAPI v1. */
@RestController
public class RoomLobbyRestController implements RoomApi {

  private final RoomLobbyService roomLobbyService;

  private final MatchmakingService matchmaking;

  public RoomLobbyRestController(
      RoomLobbyService roomLobbyService, MatchmakingService matchmaking) {
    this.matchmaking = matchmaking;
    this.roomLobbyService =
        Objects.requireNonNull(roomLobbyService, "roomLobbyService must not be null");
  }

  @Override
  public ResponseEntity<RoomResponseDto> createRoom(CreateRoomRequestDto createRoomRequestDto) {
    UserPrincipal user = getCurrentUser();
    RoomResponseDto response =
        roomLobbyService.createRoom(
            user.userId(), user.nickname(), user.avatarUrl(), user.isGuest(), createRoomRequestDto);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Override
  public ResponseEntity<RoomResponseDto> joinRoomByCode(JoinRoomRequestDto joinRoomRequestDto) {
    UserPrincipal user = getCurrentUser();
    RoomResponseDto response =
        roomLobbyService.joinRoomByCode(
            user.userId(),
            user.nickname(),
            user.avatarUrl(),
            user.isGuest(),
            joinRoomRequestDto.getCode());
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<PublicRoomPageDto> listPublicRooms(Integer page, Integer size) {
    PublicRoomPageDto result =
        roomLobbyService.listPublicRooms(page != null ? page : 0, size != null ? size : 10);
    return ResponseEntity.ok(result);
  }

  @Override
  public ResponseEntity<RoomResponseDto> quickMatch(
      @Nullable QuickMatchRequestDto quickMatchRequestDto) {
    UserPrincipal user = getCurrentUser();
    RoomResponseDto room =
        matchmaking.quickMatch(
            user.userId(), user.nickname(), user.avatarUrl(), user.isGuest(), quickMatchRequestDto);
    return ResponseEntity.ok(room);
  }

  private UserPrincipal getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Missing or invalid credentials");
    }

    UUID userId;
    try {
      userId = UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException e) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid token subject");
    }

    String nickname = jwt.getClaimAsString("nickname");
    if (nickname == null || nickname.isBlank()) {
      nickname = "Player";
    }

    String avatarUrl = jwt.getClaimAsString("avatarUrl");
    if (avatarUrl == null) {
      avatarUrl = jwt.getClaimAsString("avatar_url");
    }
    if (avatarUrl == null) {
      avatarUrl = jwt.getClaimAsString("picture");
    }

    String role = jwt.getClaimAsString("role");
    String kind = jwt.getClaimAsString("kind");
    boolean isGuest = "GUEST".equalsIgnoreCase(kind) || "GUEST".equalsIgnoreCase(role);

    return new UserPrincipal(userId, nickname, avatarUrl, isGuest);
  }

  private record UserPrincipal(
      UUID userId, String nickname, @Nullable String avatarUrl, boolean isGuest) {}
}
