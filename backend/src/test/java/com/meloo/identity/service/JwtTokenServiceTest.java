package com.meloo.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.identity.config.IdentityProperties;
import com.meloo.identity.persistence.AppUser;
import com.meloo.identity.persistence.UserKind;
import com.meloo.identity.persistence.UserRole;
import com.meloo.identity.persistence.UserStatus;
import com.nimbusds.jwt.JWTClaimsSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

  @Test
  @DisplayName("Generate, verify and read claims from valid JWT token")
  void generateAndVerifyToken() throws Exception {
    Instant fixedNow = Instant.parse("2026-10-24T10:00:00Z");
    Clock clock = Clock.fixed(fixedNow, ZoneId.of("UTC"));

    IdentityProperties properties =
        new IdentityProperties(
            new IdentityProperties.JwtProperties(
                "0123456789012345678901234567890123456789012345678901234567890123",
                Duration.ofMinutes(15),
                Duration.ofDays(30)),
            null,
            null,
            null);

    JwtTokenService tokenService = new JwtTokenService(properties, clock);

    UUID userId = UUID.randomUUID();
    AppUser user =
        AppUser.builder()
            .id(userId)
            .kind(UserKind.GUEST)
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .nickname("Meloman")
            .build();

    JwtTokenService.IssuedToken issuedToken = tokenService.generateAccessToken(user);

    assertThat(issuedToken.token()).isNotBlank();
    assertThat(issuedToken.expiresAt()).isEqualTo(fixedNow.plus(Duration.ofMinutes(15)));

    assertThat(tokenService.verifyToken(issuedToken.token())).isTrue();

    JWTClaimsSet claims = tokenService.getClaims(issuedToken.token());
    assertThat(claims.getSubject()).isEqualTo(userId.toString());
    assertThat(claims.getStringClaim("nickname")).isEqualTo("Meloman");
    assertThat(claims.getStringClaim("role")).isEqualTo("USER");
    assertThat(claims.getStringClaim("kind")).isEqualTo("GUEST");
    assertThat(claims.getIssueTime().toInstant()).isEqualTo(fixedNow);
    assertThat(claims.getExpirationTime().toInstant())
        .isEqualTo(fixedNow.plus(Duration.ofMinutes(15)));
  }

  @Test
  @DisplayName("Expired token fails verification")
  void expiredTokenFailsVerification() {
    Instant fixedNow = Instant.parse("2026-10-24T10:00:00Z");
    Clock clock = Clock.fixed(fixedNow, ZoneId.of("UTC"));

    IdentityProperties properties =
        new IdentityProperties(
            new IdentityProperties.JwtProperties(
                "0123456789012345678901234567890123456789012345678901234567890123",
                Duration.ofMinutes(15),
                Duration.ofDays(30)),
            null,
            null,
            null);

    JwtTokenService tokenService = new JwtTokenService(properties, clock);

    UUID userId = UUID.randomUUID();
    AppUser user =
        AppUser.builder()
            .id(userId)
            .kind(UserKind.GUEST)
            .role(UserRole.USER)
            .nickname("Meloman")
            .build();

    JwtTokenService.IssuedToken issuedToken = tokenService.generateAccessToken(user);

    // Fast-forward clock 16 minutes
    Clock futureClock = Clock.fixed(fixedNow.plus(Duration.ofMinutes(16)), ZoneId.of("UTC"));
    JwtTokenService futureTokenService = new JwtTokenService(properties, futureClock);

    assertThat(futureTokenService.verifyToken(issuedToken.token())).isFalse();
  }
}
