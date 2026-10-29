package com.meloo.audio.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encodes and verifies HMAC-SHA256 opaque audio tokens per ADR-008.
 *
 * <p>Binary payload layout (41 bytes):
 *
 * <ul>
 *   <li>roundId: 16 bytes (UUID msb + lsb)
 *   <li>playerId: 16 bytes (UUID msb + lsb)
 *   <li>level: 1 byte (1..5)
 *   <li>expiresAt: 8 bytes (epoch milliseconds)
 * </ul>
 *
 * Followed by 32 bytes HMAC-SHA256 signature = 73 bytes total. URL-safe Base64 without padding = 98
 * characters, strictly matching {@code ^[A-Za-z0-9_-]+$}.
 */
public class AudioTokenCodec {

  public static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
  public static final int MIN_TOKEN_LENGTH = 16;
  public static final int MAX_TOKEN_LENGTH = 256;

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final int PAYLOAD_SIZE = 16 + 16 + 1 + 8; // 41 bytes
  private static final int SIGNATURE_SIZE = 32; // 32 bytes for HMAC-SHA256
  private static final int TOTAL_BINARY_SIZE = PAYLOAD_SIZE + SIGNATURE_SIZE; // 73 bytes

  private final SecretKeySpec secretKeySpec;
  private final Clock clock;

  public AudioTokenCodec(String secret) {
    this(secret, Clock.systemUTC());
  }

  public AudioTokenCodec(String secret, Clock clock) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalArgumentException("Audio HMAC secret must not be blank");
    }
    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < 32) {
      throw new IllegalArgumentException(
          "Audio HMAC secret must be at least 32 bytes (256 bits) for HMAC-SHA256, but was "
              + keyBytes.length
              + " bytes");
    }
    this.secretKeySpec = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
    this.clock = clock;
  }

  public String encode(AudioToken audioToken) {
    if (audioToken.level() < 1 || audioToken.level() > 5) {
      throw new IllegalArgumentException(
          "Invalid listening level: " + audioToken.level() + ", must be 1..5");
    }

    ByteBuffer buffer = ByteBuffer.allocate(TOTAL_BINARY_SIZE);
    buffer.putLong(audioToken.roundId().getMostSignificantBits());
    buffer.putLong(audioToken.roundId().getLeastSignificantBits());
    buffer.putLong(audioToken.playerId().getMostSignificantBits());
    buffer.putLong(audioToken.playerId().getLeastSignificantBits());
    buffer.put((byte) audioToken.level());
    buffer.putLong(audioToken.expiresAt().toEpochMilli());

    byte[] signature = sign(buffer.array(), 0, PAYLOAD_SIZE);
    buffer.put(signature);

    return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
  }

  public AudioToken decodeAndVerify(String rawToken) {
    if (rawToken == null
        || rawToken.length() < MIN_TOKEN_LENGTH
        || rawToken.length() > MAX_TOKEN_LENGTH
        || !TOKEN_PATTERN.matcher(rawToken).matches()) {
      throw AudioTokenInvalidException.malformed();
    }

    byte[] tokenBytes;
    try {
      tokenBytes = Base64.getUrlDecoder().decode(rawToken);
    } catch (IllegalArgumentException e) {
      throw AudioTokenInvalidException.malformed();
    }

    if (tokenBytes.length != TOTAL_BINARY_SIZE) {
      throw AudioTokenInvalidException.invalidSignature();
    }

    byte[] expectedSignature = sign(tokenBytes, 0, PAYLOAD_SIZE);
    byte[] actualSignature = new byte[SIGNATURE_SIZE];
    System.arraycopy(tokenBytes, PAYLOAD_SIZE, actualSignature, 0, SIGNATURE_SIZE);

    if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
      throw AudioTokenInvalidException.invalidSignature();
    }

    ByteBuffer buffer = ByteBuffer.wrap(tokenBytes, 0, PAYLOAD_SIZE);
    long roundMsb = buffer.getLong();
    long roundLsb = buffer.getLong();
    long playerMsb = buffer.getLong();
    long playerLsb = buffer.getLong();
    int level = buffer.get() & 0xFF;
    long expiresAtEpochMilli = buffer.getLong();

    if (level < 1 || level > 5) {
      throw AudioTokenInvalidException.invalidSignature();
    }

    UUID roundId = new UUID(roundMsb, roundLsb);
    UUID playerId = new UUID(playerMsb, playerLsb);
    Instant expiresAt = Instant.ofEpochMilli(expiresAtEpochMilli);

    AudioToken token = new AudioToken(roundId, playerId, level, expiresAt);
    if (token.isExpired(clock.instant())) {
      throw new AudioTokenExpiredException();
    }

    return token;
  }

  private byte[] sign(byte[] data, int offset, int length) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(secretKeySpec);
      mac.update(data, offset, length);
      return mac.doFinal();
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("Failed to calculate HMAC signature", e);
    }
  }
}
