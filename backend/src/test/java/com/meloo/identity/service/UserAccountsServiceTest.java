package com.meloo.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meloo.identity.persistence.AppUser;
import com.meloo.identity.persistence.AppUserRepository;
import com.meloo.identity.persistence.AuthIdentityRepository;
import com.meloo.identity.persistence.RefreshTokenRepository;
import com.meloo.identity.persistence.UserKind;
import com.meloo.identity.persistence.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserAccountsServiceTest {
  @Test
  void batchProfilesExcludeGuestsLockedAndMergedAccountsAndIgnoreMissingIds() {
    var users = mock(AppUserRepository.class);
    var identities = mock(AuthIdentityRepository.class);
    var service =
        new UserAccountsService(
            users, mock(RefreshTokenRepository.class), Clock.systemUTC(), identities);
    AppUser registered = user(UserKind.REGISTERED, UserStatus.ACTIVE);
    AppUser guest = user(UserKind.GUEST, UserStatus.ACTIVE);
    AppUser locked = user(UserKind.REGISTERED, UserStatus.LOCKED);
    AppUser merged = user(UserKind.REGISTERED, UserStatus.ACTIVE);
    merged.setMergedIntoId(registered.getId());
    when(users.findAllById(any())).thenReturn(List.of(registered, guest, locked, merged));
    when(identities.findAllByUserIdIn(any())).thenReturn(List.of());
    var result =
        service.findByIdIn(
            List.of(
                registered.getId(),
                guest.getId(),
                locked.getId(),
                merged.getId(),
                UUID.randomUUID()));
    assertThat(result).containsOnlyKeys(registered.getId());
    assertThat(result.get(registered.getId()).nickname()).isEqualTo("Player");
    assertThat(service.findByIdIn(List.of())).isEmpty();
  }

  @Test
  void emptyBatchDoesNotQueryTheDatabase() {
    var users = mock(AppUserRepository.class);
    var identities = mock(AuthIdentityRepository.class);
    var service =
        new UserAccountsService(
            users, mock(RefreshTokenRepository.class), Clock.systemUTC(), identities);
    assertThat(service.findByIdIn(List.of())).isEmpty();
    verifyNoInteractions(users, identities);
  }

  private AppUser user(UserKind kind, UserStatus status) {
    return AppUser.builder()
        .id(UUID.randomUUID())
        .kind(kind)
        .status(status)
        .nickname("Player")
        .createdAt(Instant.now())
        .lastActiveAt(Instant.now())
        .build();
  }
}
