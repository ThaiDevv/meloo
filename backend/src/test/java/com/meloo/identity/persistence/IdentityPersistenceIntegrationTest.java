package com.meloo.identity.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IdentityPersistenceIntegrationTest extends IntegrationTest {

  @Autowired private AppUserRepository appUserRepository;
  @Autowired private RefreshTokenRepository refreshTokenRepository;

  @Test
  @DisplayName("AppUser round-trip with defaults and PrePersist")
  void appUserRoundTrip() {
    UUID userId = UUID.randomUUID();
    AppUser user =
        AppUser.builder()
            .id(userId)
            .kind(UserKind.REGISTERED)
            .nickname("thaidev")
            .email("test@example.com")
            .build();

    AppUser saved = appUserRepository.saveAndFlush(user);
    assertThat(saved.getRole()).isEqualTo(UserRole.USER);
    assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getLastActiveAt()).isNotNull();

    AppUser loaded = appUserRepository.findById(userId).orElseThrow();
    assertThat(loaded.getNickname()).isEqualTo("thaidev");
    assertThat(loaded.getEmail()).isEqualTo("test@example.com");
  }

  @Test
  @DisplayName("RefreshToken round-trip with char(64) tokenHash")
  void refreshTokenRoundTrip() {
    UUID userId = UUID.randomUUID();
    appUserRepository.saveAndFlush(
        AppUser.builder().id(userId).kind(UserKind.GUEST).nickname("guest1").build());

    UUID tokenId = UUID.randomUUID();
    String sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    RefreshToken token =
        RefreshToken.builder()
            .id(tokenId)
            .userId(userId)
            .familyId(UUID.randomUUID())
            .tokenHash(sha256)
            .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
            .build();

    RefreshToken saved = refreshTokenRepository.saveAndFlush(token);
    assertThat(saved.getCreatedAt()).isNotNull();

    RefreshToken loaded = refreshTokenRepository.findById(tokenId).orElseThrow();
    assertThat(loaded.getTokenHash().trim()).isEqualTo(sha256);
  }

  @Test
  @DisplayName("existsByIdAndStatus returns true only for matching id and status")
  void existsByIdAndStatus_returnsExpected() {
    UUID activeUserId = UUID.randomUUID();
    appUserRepository.saveAndFlush(
        AppUser.builder()
            .id(activeUserId)
            .kind(UserKind.GUEST)
            .nickname("activeuser")
            .status(UserStatus.ACTIVE)
            .build());

    UUID lockedUserId = UUID.randomUUID();
    appUserRepository.saveAndFlush(
        AppUser.builder()
            .id(lockedUserId)
            .kind(UserKind.GUEST)
            .nickname("lockeduser")
            .status(UserStatus.LOCKED)
            .lockReason("Terms violation")
            .lockedAt(Instant.now())
            .build());

    UUID nonExistentId = UUID.randomUUID();

    assertThat(appUserRepository.existsByIdAndStatus(lockedUserId, UserStatus.LOCKED)).isTrue();
    assertThat(appUserRepository.existsByIdAndStatus(lockedUserId, UserStatus.ACTIVE)).isFalse();

    assertThat(appUserRepository.existsByIdAndStatus(activeUserId, UserStatus.ACTIVE)).isTrue();
    assertThat(appUserRepository.existsByIdAndStatus(activeUserId, UserStatus.LOCKED)).isFalse();

    assertThat(appUserRepository.existsByIdAndStatus(nonExistentId, UserStatus.LOCKED)).isFalse();
    assertThat(appUserRepository.existsByIdAndStatus(nonExistentId, UserStatus.ACTIVE)).isFalse();
  }
}
