package com.meloo.audio.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meloo.audio.api.AudioPreparationResult;
import com.meloo.audio.config.AudioProperties;
import com.meloo.audio.domain.AudioRangeNotSatisfiableException;
import com.meloo.audio.domain.AudioToken;
import com.meloo.audio.domain.AudioTokenInvalidException;
import com.meloo.audio.domain.ListeningLevel;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

public class AudioStreamService {

  private static final Logger log = LoggerFactory.getLogger(AudioStreamService.class);
  private static final Pattern RANGE_PATTERN = Pattern.compile("^bytes=(\\d+)-(\\d+)?$");

  private final AudioProperties properties;
  private final AudioSourceResolver sourceResolver;
  private final ResourceLoader resourceLoader;
  private final RestClient restClient;

  // In-memory cache for preview URLs (TTL <= 15 minutes, not persisted to disk per ADR-003/ADR-008)
  private final Cache<UUID, String> previewCache;

  // Active round audio bindings with automatic eviction after 15m (m1)
  private final Cache<UUID, RoundAudioInfo> activeRounds;

  public record RoundAudioInfo(
      UUID roundId,
      UUID trackId,
      String previewUrl,
      long contentLength,
      java.time.Instant registeredAt) {}

  public record SlicedStreamResult(
      Resource resource, HttpStatus status, long contentLength, @Nullable String contentRange) {}

  public AudioStreamService(
      AudioProperties properties,
      AudioSourceResolver sourceResolver,
      @Nullable ResourceLoader resourceLoader,
      RestClient.Builder restClientBuilder) {
    this.properties = properties;
    this.sourceResolver = sourceResolver;
    this.resourceLoader = resourceLoader != null ? resourceLoader : new DefaultResourceLoader();
    this.restClient = restClientBuilder.build();

    this.previewCache =
        Caffeine.newBuilder()
            .expireAfterWrite(properties.previewCacheTtl())
            .maximumSize(properties.previewCacheMaxSize())
            .build();

    this.activeRounds =
        Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(15)).maximumSize(2000).build();
  }

  public AudioPreparationResult prepareRoundAudio(
      UUID roundId, UUID trackId, String provider, String providerTrackId) {
    String previewUrl = previewCache.getIfPresent(trackId);
    if (previewUrl == null) {
      Optional<String> resolved =
          sourceResolver.resolvePreviewUrl(trackId, provider, providerTrackId);
      if (resolved.isEmpty()) {
        log.warn("Could not resolve preview URL for trackId {} (provider: {})", trackId, provider);
        return AudioPreparationResult.failure("Preview URL could not be resolved from provider");
      }
      previewUrl = resolved.get();
    }

    long contentLength = probeAndGetContentLength(trackId, previewUrl);
    if (contentLength <= 0) {
      log.warn("Audio source unreachable for trackId {}", trackId);
      return AudioPreparationResult.failure("Audio source is unreachable or failed to fetch");
    }

    previewCache.put(trackId, previewUrl);
    activeRounds.put(
        roundId,
        new RoundAudioInfo(roundId, trackId, previewUrl, contentLength, java.time.Instant.now()));
    return AudioPreparationResult.success();
  }

  public AudioPreparationResult prepareRoundAudio(UUID roundId, UUID trackId, String previewUrl) {
    if (previewUrl == null || previewUrl.isBlank()) {
      return AudioPreparationResult.failure("Preview URL must not be blank");
    }

    long contentLength = probeAndGetContentLength(trackId, previewUrl);
    if (contentLength <= 0) {
      log.warn("Audio source unreachable for trackId {}", trackId);
      return AudioPreparationResult.failure("Audio source is unreachable or failed to fetch");
    }

    previewCache.put(trackId, previewUrl);
    activeRounds.put(
        roundId,
        new RoundAudioInfo(roundId, trackId, previewUrl, contentLength, java.time.Instant.now()));
    return AudioPreparationResult.success();
  }

  public boolean isRoundPlayable(UUID roundId) {
    return activeRounds.getIfPresent(roundId) != null;
  }

  public void releaseRoundAudio(UUID roundId) {
    activeRounds.invalidate(roundId);
  }

  public SlicedStreamResult streamAudioClip(AudioToken token, @Nullable String rangeHeader)
      throws IOException {
    RoundAudioInfo roundAudioInfo = activeRounds.getIfPresent(token.roundId());
    if (roundAudioInfo == null) {
      throw AudioTokenInvalidException.invalidSignature();
    }

    String previewUrl = roundAudioInfo.previewUrl();
    ListeningLevel level = ListeningLevel.fromLevel(token.level());
    long maxBytesForLevel = level.getMaxBytes();

    // M1: Use cached content length from RoundAudioInfo directly in O(1) without HEAD network
    // roundtrip
    long sourceTotalLength = roundAudioInfo.contentLength();
    long clipSize =
        sourceTotalLength > 0 ? Math.min(sourceTotalLength, maxBytesForLevel) : maxBytesForLevel;

    long start = 0;
    long end = clipSize - 1;
    boolean isPartial = false;

    if (rangeHeader != null && !rangeHeader.isBlank()) {
      Matcher matcher = RANGE_PATTERN.matcher(rangeHeader.trim());
      if (!matcher.matches()) {
        throw new AudioRangeNotSatisfiableException(clipSize);
      }

      try {
        start = Long.parseLong(matcher.group(1));
        String endGroup = matcher.group(2);
        if (endGroup != null && !endGroup.isBlank()) {
          end = Math.min(Long.parseLong(endGroup), clipSize - 1);
        } else {
          end = clipSize - 1;
        }
      } catch (NumberFormatException e) {
        throw new AudioRangeNotSatisfiableException(clipSize);
      }

      if (start < 0 || start >= clipSize || start > end) {
        throw new AudioRangeNotSatisfiableException(clipSize);
      }
      isPartial = true;
    }

    long chunkLength = end - start + 1;
    long finalStart = start;
    long finalLength = chunkLength;

    Resource boundedResource =
        new AbstractResource() {
          @Override
          public String getDescription() {
            return "Audio preview clip";
          }

          @Override
          public InputStream getInputStream() throws IOException {
            return openBoundedStream(previewUrl, finalStart, finalLength);
          }

          @Override
          public long contentLength() {
            return finalLength;
          }
        };

    if (isPartial) {
      String contentRange = "bytes " + start + "-" + end + "/" + clipSize;
      return new SlicedStreamResult(
          boundedResource, HttpStatus.PARTIAL_CONTENT, chunkLength, contentRange);
    } else {
      return new SlicedStreamResult(boundedResource, HttpStatus.OK, chunkLength, null);
    }
  }

  private long probeAndGetContentLength(UUID trackId, String previewUrl) {
    if (previewUrl.startsWith("classpath:") || previewUrl.startsWith("/fake-media/")) {
      String path =
          previewUrl.startsWith("classpath:")
              ? previewUrl
              : "classpath:" + previewUrl.replaceFirst("^/", "");
      Resource resource = resourceLoader.getResource(path);
      if (!resource.exists()) {
        return -1;
      }
      try (InputStream is = resource.getInputStream()) {
        if (is.read() == -1) {
          return -1;
        }
        return resource.contentLength();
      } catch (Exception e) {
        log.warn("Failed to probe classpath media for trackId {}: {}", trackId, e.getMessage());
        return -1;
      }
    }

    try {
      ResponseEntity<Void> response =
          restClient.head().uri(URI.create(previewUrl)).retrieve().toBodilessEntity();

      if (response.getStatusCode().is2xxSuccessful()) {
        long len = response.getHeaders().getContentLength();
        return len > 0 ? len : 480 * 1024; // Default CBR 128 kbps ~30s
      }
    } catch (Exception e) {
      log.warn("Probe HEAD request failed for trackId {}: {}", trackId, e.getMessage());
    }
    return -1;
  }

  private InputStream openBoundedStream(String previewUrl, long start, long length)
      throws IOException {
    if (previewUrl.startsWith("classpath:") || previewUrl.startsWith("/fake-media/")) {
      String path =
          previewUrl.startsWith("classpath:")
              ? previewUrl
              : "classpath:" + previewUrl.replaceFirst("^/", "");
      Resource resource = resourceLoader.getResource(path);
      InputStream is = resource.getInputStream();
      if (start > 0) {
        long skipped = is.skip(start);
        while (skipped < start) {
          long additional = is.skip(start - skipped);
          if (additional <= 0) {
            break;
          }
          skipped += additional;
        }
      }
      return new BoundedInputStream(is, length);
    }

    // Remote HTTP streaming proxy
    try {
      Resource remoteResource =
          restClient
              .get()
              .uri(URI.create(previewUrl))
              .header(HttpHeaders.RANGE, "bytes=" + start + "-" + (start + length - 1))
              .retrieve()
              .body(Resource.class);

      if (remoteResource != null) {
        return new BoundedInputStream(remoteResource.getInputStream(), length);
      }
    } catch (Exception e) {
      log.warn(
          "Range fetch failed for remote URL, falling back to full stream skip: {}",
          e.getMessage());
    }

    Resource fullResource =
        restClient.get().uri(URI.create(previewUrl)).retrieve().body(Resource.class);
    if (fullResource == null) {
      throw new IOException("Failed to open remote audio stream");
    }
    InputStream fullIs = fullResource.getInputStream();
    if (start > 0) {
      long skipped = fullIs.skip(start);
      while (skipped < start) {
        long additional = fullIs.skip(start - skipped);
        if (additional <= 0) {
          break;
        }
        skipped += additional;
      }
    }
    return new BoundedInputStream(fullIs, length);
  }

  public static class BoundedInputStream extends FilterInputStream {
    private long remaining;

    public BoundedInputStream(InputStream in, long maxBytes) {
      super(in);
      this.remaining = Math.max(0, maxBytes);
    }

    @Override
    public int read() throws IOException {
      if (remaining <= 0) {
        return -1;
      }
      int result = in.read();
      if (result != -1) {
        remaining--;
      }
      return result;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (remaining <= 0) {
        return -1;
      }
      int toRead = (int) Math.min(len, remaining);
      int read = in.read(b, off, toRead);
      if (read != -1) {
        remaining -= read;
      }
      return read;
    }

    @Override
    public long skip(long n) throws IOException {
      long toSkip = Math.min(n, remaining);
      long skipped = in.skip(toSkip);
      remaining -= skipped;
      return skipped;
    }

    @Override
    public int available() throws IOException {
      return (int) Math.min(in.available(), remaining);
    }
  }
}
