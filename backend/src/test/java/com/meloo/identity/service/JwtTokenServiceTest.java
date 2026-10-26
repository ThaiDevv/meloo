package com.meloo.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.identity.config.IdentityProperties;
import com.meloo.identity.persistence.AppUser;
import com.meloo.identity.persistence.UserKind;
import com.meloo.identity.persistence.UserRole;
import com.meloo.identity.persistence.UserStatus;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

  private static final String TEST_SECRET =
      "0123456789012345678901234567890123456789012345678901234567890123";

  @Test
  @DisplayName("Generate valid JWT token with correct claims and signature")
  void generateValidToken() throws Exception {
    Instant fixedNow = Instant.parse("2026-10-24T10:00:00Z");
    Clock clock = Clock.fixed(fixedNow, ZoneId.of("UTC"));

    IdentityProperties properties =
        new IdentityProperties(
            new IdentityProperties.JwtProperties(
                TEST_SECRET, Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofSeconds(10)),
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

    // Verify token signature with Nimbus MACVerifier directly
    SignedJWT signedJWT = SignedJWT.parse(issuedToken.token());
    MACVerifier verifier = new MACVerifier(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
    assertThat(signedJWT.verify(verifier)).isTrue();

    // Verify claims
    JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
    assertThat(claims.getSubject()).isEqualTo(userId.toString());
    assertThat(claims.getStringClaim("nickname")).isEqualTo("Meloman");
    assertThat(claims.getStringClaim("role")).isEqualTo("USER");
    assertThat(claims.getStringClaim("kind")).isEqualTo("GUEST");
    assertThat(claims.getIssueTime().toInstant()).isEqualTo(fixedNow);
    assertThat(claims.getExpirationTime().toInstant())
        .isEqualTo(fixedNow.plus(Duration.ofMinutes(15)));
  }
}
