package com.meloo.identity.service;

import com.meloo.identity.config.AdminEmailsProperties;
import com.meloo.identity.events.GuestMerged;
import com.meloo.identity.events.UserRegistered;
import com.meloo.identity.persistence.AppUser;
import com.meloo.identity.persistence.AppUserRepository;
import com.meloo.identity.persistence.AuthIdentity;
import com.meloo.identity.persistence.AuthIdentityRepository;
import com.meloo.identity.persistence.AuthProvider;
import com.meloo.identity.persistence.RefreshToken;
import com.meloo.identity.persistence.RefreshTokenRepository;
import com.meloo.identity.persistence.UserKind;
import com.meloo.identity.persistence.UserRole;
import com.meloo.identity.persistence.UserStatus;
import com.meloo.shared.api.ApiException;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthAccountService {
  private final AppUserRepository users;
  private final AuthIdentityRepository identities;
  private final RefreshTokenRepository tokens;
  private final RefreshTokenService refresh;
  private final JwtTokenService jwt;
  private final AdminEmailsProperties admins;
  private final ApplicationEventPublisher events;
  private final EntityManager entityManager;
  private final Clock clock;

  public OAuthAccountService(
      AppUserRepository users,
      AuthIdentityRepository identities,
      RefreshTokenRepository tokens,
      RefreshTokenService refresh,
      JwtTokenService jwt,
      AdminEmailsProperties admins,
      ApplicationEventPublisher events,
      EntityManager entityManager,
      Clock clock) {
    this.users = users;
    this.identities = identities;
    this.tokens = tokens;
    this.refresh = refresh;
    this.jwt = jwt;
    this.admins = admins;
    this.events = events;
    this.entityManager = entityManager;
    this.clock = clock;
  }

  /** Returns a live initiating session without rotating its refresh token. */
  @Transactional(readOnly = true)
  public Optional<UUID> initiatingUser(@Nullable String rawToken, boolean link) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }
    var token = tokens.findByTokenHash(RefreshTokenService.hashToken(rawToken));
    if (token.isEmpty()) {
      throw failure("OAUTH_STATE_INVALID");
    }
    validToken(token.get());
    AppUser user =
        users.findById(token.get().getUserId()).orElseThrow(() -> failure("OAUTH_STATE_INVALID"));
    active(user);
    return link || user.getKind() == UserKind.GUEST ? Optional.of(user.getId()) : Optional.empty();
  }

  @Transactional
  public GuestAuthService.AuthResult login(
      String subject,
      String email,
      boolean emailVerified,
      @Nullable UUID boundUserId,
      @Nullable String boundTokenHash,
      boolean link) {
    if (!emailVerified || email == null || email.isBlank() || email.length() > 254) {
      throw failure("OAUTH_EMAIL_UNVERIFIED");
    }
    if (subject == null || subject.isBlank() || subject.length() > 128) {
      throw failure("VALIDATION_FAILED");
    }
    email = email.trim().toLowerCase(Locale.ROOT);
    final String verifiedEmail = email;
    // Serialize new identities and email auto-linking across application instances, before row
    // locks.
    for (String key : new TreeSet<>(List.of("email:" + email, "google:" + subject))) {
      entityManager
          .createNativeQuery("select 1 from pg_advisory_xact_lock(hashtextextended(:key, 0))")
          .setParameter("key", key)
          .getSingleResult();
    }
    var identity = identities.findByProviderAndProviderSubject(AuthProvider.GOOGLE, subject);
    AppUser target =
        identity
            .map(i -> users.findById(i.getUserId()).orElseThrow())
            .orElseGet(() -> users.findByEmail(verifiedEmail).orElse(null));
    // Row locks are ordered consistently when merging two existing accounts.
    TreeSet<UUID> ids = new TreeSet<>();
    if (boundUserId != null) {
      ids.add(boundUserId);
    }
    if (target != null) {
      ids.add(target.getId());
    }
    Map<UUID, AppUser> locked = new HashMap<>();
    for (UUID id : ids) {
      AppUser user = users.findByIdForUpdate(id).orElseThrow(() -> failure("OAUTH_STATE_INVALID"));
      entityManager.refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
      locked.put(id, user);
    }
    AppUser bound = boundUserId == null ? null : locked.get(boundUserId);
    if (bound != null) {
      active(bound);
      RefreshToken token =
          tokens.findByTokenHash(boundTokenHash).orElseThrow(() -> failure("OAUTH_STATE_INVALID"));
      validToken(token);
      if (!token.getUserId().equals(bound.getId())) {
        throw failure("OAUTH_STATE_INVALID");
      }
    }
    if (target != null) {
      target = locked.get(target.getId());
      active(target);
    }
    if (link && bound == null) {
      throw failure("OAUTH_STATE_INVALID");
    }
    if (bound != null && bound.getKind() == UserKind.REGISTERED) {
      if (target != null && !target.getId().equals(bound.getId())) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "OAUTH_IDENTITY_ALREADY_LINKED",
            "Identity belongs to another account");
      }
      if (!link && !email.equals(bound.getEmail())) {
        throw failure("OAUTH_EMAIL_UNVERIFIED");
      }
      target = bound;
    }
    boolean registered = false;
    if (target == null) {
      // Keep the guest row as a tombstone; downstream modules move data on GuestMerged.
      target =
          users.save(
              AppUser.builder()
                  .id(UUID.randomUUID())
                  .kind(UserKind.REGISTERED)
                  .nickname(
                      bound == null
                          ? "Player_" + UUID.randomUUID().toString().substring(0, 8)
                          : bound.getNickname())
                  .email(email)
                  .createdAt(clock.instant())
                  .lastActiveAt(clock.instant())
                  .build());
      registered = true;
    }
    if (admins.emails().contains(email) && email.equals(target.getEmail())) {
      target.setRole(UserRole.ADMIN);
    }
    target.setLastActiveAt(clock.instant());
    if (identity.isEmpty()) {
      identities.save(
          AuthIdentity.builder()
              .id(UUID.randomUUID())
              .provider(AuthProvider.GOOGLE)
              .providerSubject(subject)
              .userId(target.getId())
              .createdAt(clock.instant())
              .build());
    }
    if (registered) {
      events.publishEvent(new UserRegistered(UUID.randomUUID(), target.getId(), clock.instant()));
    }
    if (bound != null && bound.getKind() == UserKind.GUEST) {
      bound.setMergedIntoId(target.getId());
      tokens.revokeAllForUser(bound.getId(), clock.instant());
      events.publishEvent(
          new GuestMerged(UUID.randomUUID(), bound.getId(), target.getId(), clock.instant()));
    }
    // Revoke the initiating registered session when replacing its cookie as well.
    if (boundTokenHash != null && bound != null && bound.getKind() == UserKind.REGISTERED) {
      tokens.findByTokenHash(boundTokenHash).ifPresent(t -> t.setRevokedAt(clock.instant()));
    }
    var issued = jwt.generateAccessToken(target);
    var session = GuestAuthService.toAuthSessionDto(issued.token(), issued.expiresAt(), target);
    return new GuestAuthService.AuthResult(
        session, refresh.createRefreshToken(target.getId()).rawToken());
  }

  private void validToken(RefreshToken token) {
    if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(clock.instant())) {
      throw failure("OAUTH_STATE_INVALID");
    }
  }

  private static void active(AppUser user) {
    if (user.getStatus() == UserStatus.LOCKED) {
      throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", "Account is locked");
    }
    if (user.getMergedIntoId() != null) {
      throw failure("OAUTH_STATE_INVALID");
    }
  }

  private static ApiException failure(String code) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, "OAuth authentication rejected");
  }
}
