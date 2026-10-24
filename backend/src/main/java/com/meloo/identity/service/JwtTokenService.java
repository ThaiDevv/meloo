package com.meloo.identity.service;

import com.meloo.identity.config.IdentityProperties;
import com.meloo.identity.persistence.AppUser;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

  private final byte[] secretKeyBytes;
  private final Duration accessTokenTtl;
  private final Clock clock;

  public JwtTokenService(IdentityProperties properties) {
    this(properties, Clock.systemUTC());
  }

  @Autowired
  public JwtTokenService(IdentityProperties properties, Clock clock) {
    this.secretKeyBytes = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
    this.accessTokenTtl = properties.jwt().accessTokenTtl();
    this.clock = clock;
  }

  public IssuedToken generateAccessToken(AppUser user) {
    Instant now = clock.instant();
    Instant expiresAt = now.plus(accessTokenTtl);

    JWTClaimsSet claimsSet =
        new JWTClaimsSet.Builder()
            .subject(user.getId().toString())
            .claim("nickname", user.getNickname())
            .claim("role", user.getRole().name())
            .claim("kind", user.getKind().name())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(expiresAt))
            .build();

    try {
      JWSSigner signer = new MACSigner(secretKeyBytes);
      SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
      signedJWT.sign(signer);
      return new IssuedToken(signedJWT.serialize(), expiresAt);
    } catch (JOSEException e) {
      throw new IllegalStateException("Failed to sign access token", e);
    }
  }

  public boolean verifyToken(String token) {
    try {
      SignedJWT signedJWT = SignedJWT.parse(token);
      JWSVerifier verifier = new MACVerifier(secretKeyBytes);
      if (!signedJWT.verify(verifier)) {
        return false;
      }
      Date expirationTime = signedJWT.getJWTClaimsSet().getExpirationTime();
      return expirationTime != null && expirationTime.toInstant().isAfter(clock.instant());
    } catch (ParseException | JOSEException e) {
      return false;
    }
  }

  public JWTClaimsSet getClaims(String token) throws ParseException, JOSEException {
    SignedJWT signedJWT = SignedJWT.parse(token);
    JWSVerifier verifier = new MACVerifier(secretKeyBytes);
    if (!signedJWT.verify(verifier)) {
      throw new IllegalArgumentException("Invalid token signature");
    }
    return signedJWT.getJWTClaimsSet();
  }

  public record IssuedToken(String token, Instant expiresAt) {}
}
