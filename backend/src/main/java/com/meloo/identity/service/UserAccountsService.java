package com.meloo.identity.service;

import com.meloo.generated.model.PresetAvatarIdDto;
import com.meloo.identity.api.UserAccount;
import com.meloo.identity.api.UserAccounts;
import com.meloo.identity.persistence.AppUser;
import com.meloo.identity.persistence.AppUserRepository;
import com.meloo.identity.persistence.AuthIdentityRepository;
import com.meloo.identity.persistence.RefreshTokenRepository;
import com.meloo.identity.persistence.UserKind;
import com.meloo.identity.persistence.UserRole;
import com.meloo.identity.persistence.UserStatus;
import com.meloo.shared.api.ApiException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserAccountsService implements UserAccounts {
  private final AppUserRepository users;
  private final RefreshTokenRepository tokens;
  private final Clock clock;
  private final AuthIdentityRepository identities;
  private final NicknameValidator nicknames;

  public UserAccountsService(
      AppUserRepository users,
      RefreshTokenRepository tokens,
      Clock clock,
      AuthIdentityRepository identities) {
    this(
        users,
        tokens,
        clock,
        identities,
        new NicknameValidator(
            new com.meloo.identity.config.IdentityProperties(
                new com.meloo.identity.config.IdentityProperties.JwtProperties(
                    "012345678901234567890123456789012", null, null, null),
                null,
                null,
                List.of())));
  }

  @Autowired
  public UserAccountsService(
      AppUserRepository users,
      RefreshTokenRepository tokens,
      Clock clock,
      AuthIdentityRepository identities,
      NicknameValidator nicknames) {
    this.users = users;
    this.tokens = tokens;
    this.clock = clock;
    this.identities = identities;
    this.nicknames = nicknames;
  }

  @Override
  public UserAccount get(UUID id) {
    AppUser user =
        users
            .findById(id)
            .filter(u -> u.getMergedIntoId() == null)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
    return view(
        user,
        identities.findAllByUserId(id).stream().map(i -> i.getProvider().name()).sorted().toList());
  }

  @Override
  public Map<UUID, UserAccount> findByIdIn(Collection<UUID> ids) {
    var distinct = ids.stream().distinct().toList();
    Map<UUID, UserAccount> result = new HashMap<>();
    // Keep each IN list below the PostgreSQL bind parameter limit for large leaderboards.
    for (int from = 0; from < distinct.size(); from += 1000) {
      result.putAll(findBatch(distinct.subList(from, Math.min(from + 1000, distinct.size()))));
    }
    return Map.copyOf(result);
  }

  private Map<UUID, UserAccount> findBatch(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    var eligible =
        users.findAllById(ids).stream()
            .filter(
                u ->
                    u.getKind() == UserKind.REGISTERED
                        && u.getStatus() == UserStatus.ACTIVE
                        && u.getMergedIntoId() == null)
            .toList();
    var providers =
        identities.findAllByUserIdIn(eligible.stream().map(AppUser::getId).toList()).stream()
            .collect(
                Collectors.groupingBy(
                    i -> i.getUserId(),
                    Collectors.mapping(i -> i.getProvider().name(), Collectors.toList())));
    return eligible.stream()
        .collect(
            Collectors.toUnmodifiableMap(
                AppUser::getId,
                u ->
                    view(
                        u,
                        providers.getOrDefault(u.getId(), List.of()).stream().sorted().toList())));
  }

  @Override
  @Transactional
  public UserAccount updateNickname(UUID id, String nickname) {
    return updateProfile(id, nickname, null);
  }

  @Override
  @Transactional
  public UserAccount updateProfile(UUID id, @Nullable String nickname, @Nullable String avatarId) {
    AppUser user = requireForUpdate(id);
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", "Account is locked");
    }
    if (user.getKind() != UserKind.REGISTERED) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "GUEST_PROFILE_READ_ONLY", "Guest profiles are read-only");
    }
    if (nickname != null) {
      nicknames.validate(nickname);
    }
    if (avatarId != null) {
      try {
        PresetAvatarIdDto.fromValue(avatarId);
      } catch (IllegalArgumentException ex) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AVATAR", "Invalid avatar preset");
      }
    }
    if (nickname != null) {
      user.setNickname(Normalizer.normalize(nickname.trim(), Normalizer.Form.NFC));
    }
    if (avatarId != null) {
      user.setAvatarId(avatarId);
    }
    if (nickname != null && avatarId != null) {
      user.setProfileCompleted(true);
    }
    return get(id);
  }

  @Override
  public UserPage search(String query, int page, int size) {
    if (query == null || query.length() > 200 || page < 0 || size < 1 || size > 100) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid user search");
    }
    var result =
        users.search(
            query,
            PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id"))));
    Map<UUID, List<String>> providers =
        identities
            .findAllByUserIdIn(result.getContent().stream().map(AppUser::getId).toList())
            .stream()
            .collect(
                Collectors.groupingBy(
                    i -> i.getUserId(),
                    Collectors.mapping(i -> i.getProvider().name(), Collectors.toList())));
    return new UserPage(
        result.getContent().stream()
            .map(
                u ->
                    view(
                        u, providers.getOrDefault(u.getId(), List.of()).stream().sorted().toList()))
            .toList(),
        result.getTotalElements(),
        page,
        size);
  }

  @Override
  @Transactional
  public void setLocked(UUID id, boolean locked, String reason) {
    if (locked && (reason == null || reason.isBlank() || reason.length() > 500)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_FAILED",
          "A lock reason of 1-500 characters is required");
    }
    AppUser user = requireForUpdate(id);
    user.setStatus(locked ? UserStatus.LOCKED : UserStatus.ACTIVE);
    user.setLockReason(locked ? reason.trim() : null);
    user.setLockedAt(locked ? clock.instant() : null);
    if (locked) {
      tokens.revokeAllForUser(id, clock.instant());
    }
  }

  @Override
  @Transactional
  public void setRole(UUID id, String role) {
    UserRole value;
    try {
      value = UserRole.valueOf(role);
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid role");
    }
    AppUser user = requireForUpdate(id);
    user.setRole(value);
    tokens.revokeAllForUser(id, clock.instant());
  }

  @Override
  public long countCreated(String kind, Instant from, Instant to) {
    UserKind value;
    try {
      value = UserKind.valueOf(kind);
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid user kind");
    }
    if (from == null || to == null || !from.isBefore(to)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid creation interval");
    }
    return users.countCreated(value, from, to);
  }

  private AppUser requireForUpdate(UUID id) {
    return users
        .findByIdForUpdate(id)
        .filter(u -> u.getMergedIntoId() == null)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
  }

  private static UserAccount view(AppUser u, List<String> providers) {
    return new UserAccount(
        u.getId(),
        u.getKind().name(),
        u.getRole().name(),
        u.getStatus().name(),
        u.getNickname(),
        u.getEmail(),
        u.getLockReason(),
        u.getLockedAt(),
        u.getLastActiveAt(),
        providers,
        u.getCreatedAt(),
        u.getAvatarId(),
        u.isProfileCompleted());
  }
}
