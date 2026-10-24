package com.meloo.identity.service;

import com.meloo.identity.config.IdentityProperties;
import com.meloo.identity.persistence.RefreshToken;
import com.meloo.identity.persistence.RefreshTokenRepository;
import com.meloo.shared.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

  private final RefreshTokenRepository refreshTokenRepository;
  private final Duration refreshTokenTtl;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();

  public RefreshTokenService(
      RefreshTokenRepository refreshTokenRepository, IdentityProperties properties) {
    this(refreshTokenRepository, properties, Clock.systemUTC());
  }

  @Autowired
  public RefreshTokenService(
      RefreshTokenRepository refreshTokenRepository, IdentityProperties properties, Clock clock) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.refreshTokenTtl = properties.jwt().refreshTokenTtl();
    this.clock = clock;
  }

  public String generateRawToken() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static String hashToken(String rawToken) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }

  @Transactional
  public IssuedRefreshToken createRefreshToken(UUID userId) {
    String rawToken = generateRawToken();
    String tokenHash = hashToken(rawToken);
    Instant now = clock.instant();
    Instant expiresAt = now.plus(refreshTokenTtl);
    UUID familyId = UUID.randomUUID();
    UUID tokenId = UUID.randomUUID();

    RefreshToken token =
        RefreshToken.builder()
            .id(tokenId)
            .userId(userId)
            .familyId(familyId)
            .tokenHash(tokenHash)
            .expiresAt(expiresAt)
            .createdAt(now)
            .build();

    refreshTokenRepository.save(token);
    return new IssuedRefreshToken(rawToken, token);
  }

  @Transactional(noRollbackFor = ApiException.class)
  public IssuedRefreshToken rotateRefreshToken(String rawToken) {
    String tokenHash = hashToken(rawToken);
    Optional<RefreshToken> optionalToken = refreshTokenRepository.findByTokenHash(tokenHash);

    if (optionalToken.isEmpty()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid refresh token");
    }

    RefreshToken existingToken = optionalToken.get();
    Instant now = clock.instant();

    // Check if token was already revoked: Reuse detected!
    if (existingToken.getRevokedAt() != null) {
      refreshTokenRepository.revokeAllInFamily(existingToken.getFamilyId(), now);
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Refresh token reuse detected");
    }

    // Check if expired
    if (existingToken.getExpiresAt().isBefore(now)) {
      existingToken.setRevokedAt(now);
      refreshTokenRepository.save(existingToken);
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Refresh token has expired");
    }

    // Issue new token in same family
    String newRawToken = generateRawToken();
    String newTokenHash = hashToken(newRawToken);
    UUID newId = UUID.randomUUID();
    Instant newExpiresAt = now.plus(refreshTokenTtl);

    RefreshToken newToken =
        RefreshToken.builder()
            .id(newId)
            .userId(existingToken.getUserId())
            .familyId(existingToken.getFamilyId())
            .tokenHash(newTokenHash)
            .expiresAt(newExpiresAt)
            .createdAt(now)
            .build();

    // Revoke old token and link replaced_by
    existingToken.setRevokedAt(now);
    existingToken.setReplacedById(newId);

    refreshTokenRepository.save(existingToken);
    refreshTokenRepository.save(newToken);

    return new IssuedRefreshToken(newRawToken, newToken);
  }

  @Transactional
  public void revokeToken(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return;
    }
    String tokenHash = hashToken(rawToken);
    refreshTokenRepository
        .findByTokenHash(tokenHash)
        .ifPresent(
            token -> {
              if (token.getRevokedAt() == null) {
                token.setRevokedAt(clock.instant());
                refreshTokenRepository.save(token);
              }
            });
  }

  public record IssuedRefreshToken(String rawToken, RefreshToken tokenEntity) {}
}
