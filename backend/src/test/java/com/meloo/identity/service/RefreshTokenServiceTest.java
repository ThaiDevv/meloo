package com.meloo.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meloo.identity.config.IdentityProperties;
import com.meloo.identity.persistence.AppUser;
import com.meloo.identity.persistence.AppUserRepository;
import com.meloo.identity.persistence.RefreshToken;
import com.meloo.identity.persistence.RefreshTokenRepository;
import com.meloo.identity.persistence.UserStatus;
import com.meloo.shared.api.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private AppUserRepository appUserRepository;

  private RefreshTokenService refreshTokenService;
  private Instant fixedNow;
  private Clock clock;

  @BeforeEach
  void setUp() {
    fixedNow = Instant.parse("2026-10-24T12:00:00Z");
    clock = Clock.fixed(fixedNow, ZoneId.of("UTC"));
    IdentityProperties properties =
        new IdentityProperties(
            new IdentityProperties.JwtProperties(
                "secret", Duration.ofMinutes(15), Duration.ofDays(30)),
            null,
            null,
            null);
    refreshTokenService =
        new RefreshTokenService(refreshTokenRepository, appUserRepository, properties, clock);
  }

  @Test
  @DisplayName("Create refresh token saves SHA-256 hash (64 hex chars)")
  void createRefreshTokenSavesHash() {
    UUID userId = UUID.randomUUID();

    RefreshTokenService.IssuedRefreshToken result = refreshTokenService.createRefreshToken(userId);

    assertThat(result.rawToken()).isNotBlank();
    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenRepository).save(captor.capture());

    RefreshToken saved = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo(userId);
    assertThat(saved.getTokenHash()).hasSize(64);
    assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenService.hashToken(result.rawToken()));
    assertThat(saved.getExpiresAt()).isEqualTo(fixedNow.plus(Duration.ofDays(30)));
    assertThat(saved.getRevokedAt()).isNull();
  }

  @Test
  @DisplayName(
      "Rotate unrevoked valid token creates new token in same family and marks old replaced")
  void rotateRefreshTokenSuccess() {
    UUID userId = UUID.randomUUID();
    UUID familyId = UUID.randomUUID();
    UUID oldTokenId = UUID.randomUUID();
    String rawToken = "old-raw-token";
    String tokenHash = RefreshTokenService.hashToken(rawToken);

    RefreshToken existing =
        RefreshToken.builder()
            .id(oldTokenId)
            .userId(userId)
            .familyId(familyId)
            .tokenHash(tokenHash)
            .expiresAt(fixedNow.plus(Duration.ofDays(29)))
            .createdAt(fixedNow.minus(Duration.ofDays(1)))
            .build();

    AppUser user = AppUser.builder().id(userId).status(UserStatus.ACTIVE).build();

    when(refreshTokenRepository.findByTokenHashForUpdate(tokenHash))
        .thenReturn(Optional.of(existing));
    when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));

    RefreshTokenService.IssuedRefreshToken result =
        refreshTokenService.rotateRefreshToken(rawToken);

    assertThat(result.rawToken()).isNotEqualTo(rawToken);
    assertThat(existing.getRevokedAt()).isEqualTo(fixedNow);
    assertThat(existing.getReplacedById()).isNotNull();

    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(captor.capture());

    RefreshToken newToken = captor.getAllValues().get(1);
    assertThat(newToken.getFamilyId()).isEqualTo(familyId);
    assertThat(newToken.getUserId()).isEqualTo(userId);
    assertThat(existing.getReplacedById()).isEqualTo(newToken.getId());
  }

  @Test
  @DisplayName(
      "Locked user cannot rotate refresh token: old token revoked, no new token issued (403)")
  void lockedUserCannotRotate() {
    UUID userId = UUID.randomUUID();
    UUID familyId = UUID.randomUUID();
    UUID oldTokenId = UUID.randomUUID();
    String rawToken = "locked-raw-token";
    String tokenHash = RefreshTokenService.hashToken(rawToken);

    RefreshToken existing =
        RefreshToken.builder()
            .id(oldTokenId)
            .userId(userId)
            .familyId(familyId)
            .tokenHash(tokenHash)
            .expiresAt(fixedNow.plus(Duration.ofDays(29)))
            .createdAt(fixedNow.minus(Duration.ofDays(1)))
            .build();

    AppUser lockedUser = AppUser.builder().id(userId).status(UserStatus.LOCKED).build();

    when(refreshTokenRepository.findByTokenHashForUpdate(tokenHash))
        .thenReturn(Optional.of(existing));
    when(appUserRepository.findById(userId)).thenReturn(Optional.of(lockedUser));

    assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.status()).isEqualTo(HttpStatus.FORBIDDEN);
              assertThat(apiEx.errorCode()).isEqualTo("ACCOUNT_LOCKED");
            });

    assertThat(existing.getRevokedAt()).isEqualTo(fixedNow);
    assertThat(existing.getReplacedById()).isNull();
    verify(refreshTokenRepository).save(existing);
  }

  @Test
  @DisplayName("Reusing an already revoked refresh token revokes entire family and returns 401")
  void reuseRevokedTokenRevokesFamily() {
    UUID userId = UUID.randomUUID();
    UUID familyId = UUID.randomUUID();
    String rawToken = "stolen-old-token";
    String tokenHash = RefreshTokenService.hashToken(rawToken);

    RefreshToken alreadyRevoked =
        RefreshToken.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .familyId(familyId)
            .tokenHash(tokenHash)
            .expiresAt(fixedNow.plus(Duration.ofDays(20)))
            .revokedAt(fixedNow.minus(Duration.ofHours(1)))
            .build();

    when(refreshTokenRepository.findByTokenHashForUpdate(tokenHash))
        .thenReturn(Optional.of(alreadyRevoked));

    assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
              assertThat(apiEx.errorCode()).isEqualTo("UNAUTHORIZED");
            });

    verify(refreshTokenRepository).revokeAllInFamily(familyId, fixedNow);
  }

  @Test
  @DisplayName("Expired token cannot be rotated")
  void expiredTokenCannotBeRotated() {
    UUID userId = UUID.randomUUID();
    UUID familyId = UUID.randomUUID();
    String rawToken = "expired-token";
    String tokenHash = RefreshTokenService.hashToken(rawToken);

    RefreshToken expired =
        RefreshToken.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .familyId(familyId)
            .tokenHash(tokenHash)
            .expiresAt(fixedNow.minus(Duration.ofSeconds(1)))
            .build();

    when(refreshTokenRepository.findByTokenHashForUpdate(tokenHash))
        .thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
              assertThat(apiEx.errorCode()).isEqualTo("UNAUTHORIZED");
            });

    verify(refreshTokenRepository, never()).revokeAllInFamily(eq(familyId), any());
    assertThat(expired.getRevokedAt()).isEqualTo(fixedNow);
  }
}
