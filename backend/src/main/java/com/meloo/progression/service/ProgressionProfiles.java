package com.meloo.progression.service;

import com.meloo.generated.model.OAuthProviderDto;
import com.meloo.generated.model.UserKindDto;
import com.meloo.generated.model.UserProfileDto;
import com.meloo.generated.model.UserRoleDto;
import com.meloo.identity.api.UserAccount;
import com.meloo.identity.api.UserAccounts;
import com.meloo.shared.api.ApiException;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProgressionProfiles {
  private final UserAccounts accounts;
  private final ProgressionQueries queries;

  public ProgressionProfiles(UserAccounts accounts, ProgressionQueries queries) {
    this.accounts = accounts;
    this.queries = queries;
  }

  public UserProfileDto get(UUID caller) {
    UserAccount account = accounts.get(caller);
    if (!"ACTIVE".equals(account.status())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", "Account is locked");
    }
    return profile(account);
  }

  @Transactional
  public UserProfileDto update(UUID caller, @Nullable String nickname, @Nullable String avatarId) {
    return profile(accounts.updateProfile(caller, nickname, avatarId));
  }

  private UserProfileDto profile(UserAccount account) {
    var stats = queries.stats(account.id());
    return new UserProfileDto(
            account.id(),
            UserKindDto.valueOf(account.kind()),
            UserRoleDto.valueOf(account.role()),
            account.nickname(),
            stats.getRankPoints(),
            stats.getXp(),
            account.linkedProviders().stream().map(OAuthProviderDto::fromValue).toList(),
            account.createdAt().atOffset(ZoneOffset.UTC))
        .email(account.email())
        .avatarId(
            account.avatarId() == null
                ? null
                : UserProfileDto.AvatarIdEnum.fromValue(account.avatarId()))
        .profileCompleted(account.profileCompleted());
  }
}
