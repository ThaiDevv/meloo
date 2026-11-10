package com.meloo.identity.service;

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
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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

  public UserAccountsService(
      AppUserRepository users,
      RefreshTokenRepository tokens,
      Clock clock,
      AuthIdentityRepository identities) {
    this.users = users;
    this.tokens = tokens;
    this.clock = clock;
    this.identities = identities;
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
    // Bound IN parameters independently of the number of players on a leaderboard.
    for (int offset = 0; offset < distinct.size(); offset += 1000) {
      var batch = distinct.subList(offset, Math.min(offset + 1000, distinct.size()));
      var eligible =
          users.findAllById(batch).stream()
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
      for (var user : eligible) {
        result.put(
            user.getId(),
            view(user, providers.getOrDefault(user.getId(), List.of()).stream().sorted().toList()));
      }
    }
    return Map.copyOf(result);
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
        u.getCreatedAt());
  }
}
