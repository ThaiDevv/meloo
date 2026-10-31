package com.meloo.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meloo.identity.persistence.AppUserRepository;
import com.meloo.identity.persistence.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountLockQueryImplTest {

  @Mock private AppUserRepository appUserRepository;

  @InjectMocks private AccountLockQueryImpl accountLockQuery;

  @Test
  @DisplayName("isLocked returns true when user status is LOCKED")
  void isLocked_userLocked_returnsTrue() {
    UUID userId = UUID.randomUUID();
    when(appUserRepository.existsByIdAndStatus(userId, UserStatus.LOCKED)).thenReturn(true);

    boolean locked = accountLockQuery.isLocked(userId);

    assertThat(locked).isTrue();
    verify(appUserRepository).existsByIdAndStatus(userId, UserStatus.LOCKED);
  }

  @Test
  @DisplayName("isLocked returns false when user status is ACTIVE")
  void isLocked_userActive_returnsFalse() {
    UUID userId = UUID.randomUUID();
    when(appUserRepository.existsByIdAndStatus(userId, UserStatus.LOCKED)).thenReturn(false);

    boolean locked = accountLockQuery.isLocked(userId);

    assertThat(locked).isFalse();
    verify(appUserRepository).existsByIdAndStatus(userId, UserStatus.LOCKED);
  }

  @Test
  @DisplayName("isLocked returns false when user ID does not exist")
  void isLocked_userNotFound_returnsFalse() {
    UUID nonExistentUserId = UUID.randomUUID();
    when(appUserRepository.existsByIdAndStatus(nonExistentUserId, UserStatus.LOCKED))
        .thenReturn(false);

    boolean locked = accountLockQuery.isLocked(nonExistentUserId);

    assertThat(locked).isFalse();
    verify(appUserRepository).existsByIdAndStatus(nonExistentUserId, UserStatus.LOCKED);
  }

  @Test
  @DisplayName("isLocked returns false when user ID is null")
  void isLocked_nullUserId_returnsFalse() {
    boolean locked = accountLockQuery.isLocked(null);

    assertThat(locked).isFalse();
    verifyNoInteractions(appUserRepository);
  }
}
