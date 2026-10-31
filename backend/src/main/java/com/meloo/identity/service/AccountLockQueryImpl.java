package com.meloo.identity.service;

import com.meloo.identity.api.AccountLockQuery;
import com.meloo.identity.persistence.AppUserRepository;
import com.meloo.identity.persistence.UserStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link AccountLockQuery} querying user lock state via {@link
 * AppUserRepository}.
 *
 * <p>Operates read-only over the primary key index of {@code app_users}. Returns {@code false} if
 * the user record does not exist.
 */
@Service
@Transactional(readOnly = true)
public class AccountLockQueryImpl implements AccountLockQuery {

  private final AppUserRepository appUserRepository;

  public AccountLockQueryImpl(AppUserRepository appUserRepository) {
    this.appUserRepository = appUserRepository;
  }

  @Override
  public boolean isLocked(UUID userId) {
    if (userId == null) {
      return false;
    }
    return appUserRepository.existsByIdAndStatus(userId, UserStatus.LOCKED);
  }
}
