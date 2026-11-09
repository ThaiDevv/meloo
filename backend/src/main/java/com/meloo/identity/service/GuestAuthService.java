package com.meloo.identity.service;

import com.meloo.generated.model.AuthSessionDto;
import com.meloo.generated.model.GuestRequestDto;
import com.meloo.generated.model.MeDto;
import com.meloo.generated.model.UserKindDto;
import com.meloo.generated.model.UserRoleDto;
import com.meloo.identity.events.GuestCreated;
import com.meloo.identity.persistence.AppUser;
import com.meloo.identity.persistence.AppUserRepository;
import com.meloo.identity.persistence.UserKind;
import com.meloo.identity.persistence.UserRole;
import com.meloo.identity.persistence.UserStatus;
import com.meloo.shared.api.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuestAuthService {

  private final AppUserRepository appUserRepository;
  private final JwtTokenService jwtTokenService;
  private final RefreshTokenService refreshTokenService;
  private final NicknameValidator nicknameValidator;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public GuestAuthService(
      AppUserRepository appUserRepository,
      JwtTokenService jwtTokenService,
      RefreshTokenService refreshTokenService,
      NicknameValidator nicknameValidator,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.appUserRepository = appUserRepository;
    this.jwtTokenService = jwtTokenService;
    this.refreshTokenService = refreshTokenService;
    this.nicknameValidator = nicknameValidator;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  @Transactional
  public AuthResult createGuest(GuestRequestDto request) {
    if (request == null || request.getNickname() == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Nickname is required");
    }
    String normalizedNickname =
        java.text.Normalizer.normalize(request.getNickname().trim(), java.text.Normalizer.Form.NFC);
    nicknameValidator.validate(normalizedNickname);

    UUID userId = UUID.randomUUID();
    Instant now = clock.instant();
    AppUser user =
        AppUser.builder()
            .id(userId)
            .kind(UserKind.GUEST)
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .nickname(normalizedNickname)
            .createdAt(now)
            .lastActiveAt(now)
            .build();

    appUserRepository.save(user);

    eventPublisher.publishEvent(
        new GuestCreated(UUID.randomUUID(), user.getId(), user.getNickname(), now));

    JwtTokenService.IssuedToken accessToken = jwtTokenService.generateAccessToken(user);
    RefreshTokenService.IssuedRefreshToken refreshToken =
        refreshTokenService.createRefreshToken(user.getId());

    AuthSessionDto session = toAuthSessionDto(accessToken.token(), accessToken.expiresAt(), user);
    return new AuthResult(session, refreshToken.rawToken());
  }

  @Transactional(noRollbackFor = ApiException.class)
  public AuthResult refreshSession(
      String rawRefreshToken, String origin, List<String> allowedOrigins) {
    validateOrigin(origin, allowedOrigins);

    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Missing refresh token");
    }

    RefreshTokenService.IssuedRefreshToken rotated =
        refreshTokenService.rotateRefreshToken(rawRefreshToken);
    AppUser user = rotated.user();
    if (user == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User not found");
    }

    user.setLastActiveAt(clock.instant());
    appUserRepository.save(user);

    JwtTokenService.IssuedToken newAccessToken = jwtTokenService.generateAccessToken(user);
    AuthSessionDto session =
        toAuthSessionDto(newAccessToken.token(), newAccessToken.expiresAt(), user);

    return new AuthResult(session, rotated.rawToken());
  }

  @Transactional
  public void logout(String rawRefreshToken, String origin, List<String> allowedOrigins) {
    validateOrigin(origin, allowedOrigins);
    refreshTokenService.revokeToken(rawRefreshToken);
  }

  @Transactional
  public void logout(String rawRefreshToken) {
    logout(rawRefreshToken, null, List.of());
  }

  @Transactional(readOnly = true)
  public MeDto getMe(UUID userId) {
    AppUser user =
        appUserRepository
            .findById(userId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User not found"));

    if (user.getStatus() == UserStatus.LOCKED || user.getMergedIntoId() != null) {
      throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", "Account is locked");
    }

    return toMeDto(user);
  }

  private void validateOrigin(String origin, List<String> allowedOrigins) {
    if (origin != null && !origin.isBlank() && !allowedOrigins.isEmpty()) {
      boolean allowed =
          allowedOrigins.stream()
              .anyMatch(allowedOrigin -> allowedOrigin.equalsIgnoreCase(origin.trim()));
      if (!allowed) {
        throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Origin not allowed");
      }
    }
  }

  public static MeDto toMeDto(AppUser user) {
    return new MeDto()
        .id(user.getId())
        .kind(UserKindDto.fromValue(user.getKind().name()))
        .role(UserRoleDto.fromValue(user.getRole().name()))
        .nickname(user.getNickname())
        .email(user.getEmail())
        .createdAt(user.getCreatedAt().atOffset(ZoneOffset.UTC));
  }

  public static AuthSessionDto toAuthSessionDto(
      String accessToken, Instant expiresAt, AppUser user) {
    return new AuthSessionDto()
        .accessToken(accessToken)
        .tokenType(AuthSessionDto.TokenTypeEnum.BEARER)
        .expiresAt(expiresAt.atOffset(ZoneOffset.UTC))
        .user(toMeDto(user));
  }

  public record AuthResult(AuthSessionDto session, String rawRefreshToken) {}
}
