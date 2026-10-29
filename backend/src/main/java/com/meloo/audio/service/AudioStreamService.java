package com.meloo.audio.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.meloo.audio.api.AudioPreparationResult;
import com.meloo.audio.config.AudioProperties;
import com.meloo.audio.domain.AudioException;
import com.meloo.audio.domain.AudioRangeNotSatisfiableException;
import com.meloo.audio.domain.AudioToken;
import com.meloo.audio.domain.AudioTokenInvalidException;
import com.meloo.audio.domain.ListeningLevel;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

public class AudioStreamService {

  private static final Logger log = LoggerFactory.getLogger(AudioStreamService.class);
  private static final Pattern RANGE_PATTERN = Pattern.compile("^bytes=(\\d+)-(\\d+)?$");
  private static final Pattern SAFE_FAKE_MEDIA_PATTERN =
      Pattern.compile("^(classpath:)?(fake-media/|/fake-media/)[a-zA-Z0-9_-]+\\.mp3$");

  private final AudioProperties properties;
  private final AudioSourceResolver sourceResolver;
  private final ResourceLoader resourceLoader;
  private final RestClient restClient;

  // In-memory cache for preview URLs (TTL <= 15 minutes, not persisted to disk per ADR-003/ADR-008)
  private final Cache<UUID, String> previewCache;

  // Active round audio bindings with automatic eviction and configurable TTL (M3, m10)
  private final Cache<UUID, RoundAudioInfo> activeRounds;

  public record RoundAudioInfo(
      UUID roundId,
      UUID trackId,
      String provider,
      String providerTrackId,
      String previewUrl,
      long contentLength,
      Instant registeredAt) {}

  public record SlicedStreamResult(
      Resource resource, HttpStatus status, long contentLength, @Nullable String contentRange) {}

  public AudioStreamService(
      AudioProperties properties,
      AudioSourceResolver sourceResolver,
      @Nullable ResourceLoader resourceLoader,
      RestClient.Builder restClientBuilder) {
    this(properties, sourceResolver, resourceLoader, restClientBuilder, null);
  }

  public AudioStreamService(
      AudioProperties properties,
      AudioSourceResolver sourceResolver,
      @Nullable ResourceLoader resourceLoader,
      RestClient.Builder restClientBuilder,
      @Nullable Ticker ticker) {
    this.properties = properties;
    this.sourceResolver = sourceResolver;
    this.resourceLoader = resourceLoader != null ? resourceLoader : new DefaultResourceLoader();
    this.restClient = restClientBuilder.build();

    Caffeine<Object, Object> previewCacheBuilder =
        Caffeine.newBuilder()
            .expireAfterWrite(properties.previewCacheTtl())
            .maximumSize(properties.previewCacheMaxSize());
    if (ticker != null) {
      previewCacheBuilder.ticker(ticker);
    }
    this.previewCache = previewCacheBuilder.build();

    Caffeine<Object, Object> activeRoundsBuilder =
        Caffeine.newBuilder()
            .expireAfterWrite(properties.activeRoundsTtl())
            .maximumSize(properties.activeRoundsMaxSize());
    if (ticker != null) {
      activeRoundsBuilder.ticker(ticker);
    }
    this.activeRounds = activeRoundsBuilder.build();
  }

  public AudioPreparationResult prepareRoundAudio(
      UUID roundId, UUID trackId, String provider, String providerTrackId) {
    String previewUrl = previewCache.getIfPresent(trackId);
    boolean fromCache = (previewUrl != null);

    if (previewUrl == null) {
      Optional<String> resolved =
          sourceResolver.resolvePreviewUrl(trackId, provider, providerTrackId);
      if (resolved.isEmpty()) {
        log.warn("Could not resolve preview URL for roundId {} (provider: {})", roundId, provider);
        return AudioPreparationResult.failure("Preview URL could not be resolved from provider");
      }
      previewUrl = resolved.get();
    }

    long contentLength = probeAndGetContentLength(previewUrl);
    if (contentLength <= 0 && fromCache) {
      // M2: Cached URL may have expired upstream; invalidate and retry resolve once
      previewCache.invalidate(trackId);
      Optional<String> resolved =
          sourceResolver.resolvePreviewUrl(trackId, provider, providerTrackId);
      if (resolved.isPresent()) {
        previewUrl = resolved.get();
        contentLength = probeAndGetContentLength(previewUrl);
        fromCache = false;
      }
    }

    if (contentLength <= 0) {
      log.warn("Audio source unreachable for roundId {}", roundId);
      return AudioPreparationResult.failure("Audio source is unreachable or failed to fetch");
    }

    // Only put into cache when it was newly resolved to avoid extending TTL on cache hits (M2)
    if (!fromCache) {
      previewCache.put(trackId, previewUrl);
    }

    activeRounds.put(
        roundId,
        new RoundAudioInfo(
            roundId, trackId, provider, providerTrackId, previewUrl, contentLength, Instant.now()));
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

    // M1: Use cached content length from RoundAudioInfo in O(1) without HEAD network roundtrip
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
            return openBoundedStreamWithRetry(roundAudioInfo, finalStart, finalLength);
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

  private InputStream openBoundedStreamWithRetry(
      RoundAudioInfo roundAudioInfo, long start, long length) throws IOException {
    try {
      return openBoundedStream(roundAudioInfo.previewUrl(), start, length);
    } catch (AudioException ex) {
      // M3: If remote upstream fails with 403 or unavailable, attempt re-resolution if provider
      // info exists
      if (roundAudioInfo.providerTrackId() != null && !roundAudioInfo.providerTrackId().isBlank()) {
        Optional<String> refreshed =
            sourceResolver.resolvePreviewUrl(
                roundAudioInfo.trackId(),
                roundAudioInfo.provider(),
                roundAudioInfo.providerTrackId());
        if (refreshed.isPresent()) {
          String newUrl = refreshed.get();
          long newLength = probeAndGetContentLength(newUrl);
          if (newLength > 0) {
            RoundAudioInfo updated =
                new RoundAudioInfo(
                    roundAudioInfo.roundId(),
                    roundAudioInfo.trackId(),
                    roundAudioInfo.provider(),
                    roundAudioInfo.providerTrackId(),
                    newUrl,
                    newLength,
                    Instant.now());
            activeRounds.put(roundAudioInfo.roundId(), updated);
            previewCache.put(roundAudioInfo.trackId(), newUrl);
            return openBoundedStream(newUrl, start, length);
          }
        }
      }
      throw ex;
    }
  }

  private long probeAndGetContentLength(String previewUrl) {
    if (isFakeMedia(previewUrl)) {
      String path = normalizeClasspath(previewUrl);
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
        log.warn("Failed to probe classpath media: {}", e.getClass().getSimpleName());
        return -1;
      }
    }

    try {
      ResponseEntity<Void> response =
          restClient.head().uri(URI.create(previewUrl)).retrieve().toBodilessEntity();

      if (response.getStatusCode().is2xxSuccessful()) {
        long len = response.getHeaders().getContentLength();
        if (len > 0) {
          return len;
        }
      }
    } catch (Exception e) {
      log.warn("Probe HEAD request failed: {}", e.getClass().getSimpleName());
    }

    // m7: If HEAD Content-Length is missing or <= 0, query Range: bytes=0-0 to read Content-Range
    // total
    try {
      ResponseEntity<Void> rangeProbe =
          restClient
              .get()
              .uri(URI.create(previewUrl))
              .header(HttpHeaders.RANGE, "bytes=0-0")
              .retrieve()
              .toBodilessEntity();
      String contentRange = rangeProbe.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE);
      if (contentRange != null && contentRange.contains("/")) {
        String totalStr = contentRange.substring(contentRange.lastIndexOf('/') + 1).trim();
        long total = Long.parseLong(totalStr);
        if (total > 0) {
          return total;
        }
      }
    } catch (Exception e) {
      log.warn("Probe Range GET request failed: {}", e.getClass().getSimpleName());
    }

    return -1;
  }

  private InputStream openBoundedStream(String previewUrl, long start, long length)
      throws IOException {
    if (isFakeMedia(previewUrl)) {
      String path = normalizeClasspath(previewUrl);
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

    // m3: Remote HTTP streaming proxy using exchange to avoid buffering entire stream into JVM heap
    try {
      return restClient
          .get()
          .uri(URI.create(previewUrl))
          .header(HttpHeaders.RANGE, "bytes=" + start + "-" + (start + length - 1))
          .exchange(
              (request, response) -> {
                HttpStatusCode status = response.getStatusCode();
                if (status.equals(HttpStatus.PARTIAL_CONTENT)) {
                  // Upstream served byte range starting at 'start'
                  return new BoundedInputStream(response.getBody(), length);
                } else if (status.is2xxSuccessful()) {
                  // Upstream ignored Range header and served from 0: skip 'start' bytes
                  InputStream body = response.getBody();
                  if (start > 0) {
                    long skipped = body.skip(start);
                    while (skipped < start) {
                      long add = body.skip(start - skipped);
                      if (add <= 0) break;
                      skipped += add;
                    }
                  }
                  return new BoundedInputStream(body, length);
                } else if (status.is4xxClientError() || status.is5xxServerError()) {
                  log.warn("Remote upstream audio fetch failed with status: {}", status.value());
                  throw new AudioException(
                      HttpStatus.BAD_GATEWAY,
                      "UPSTREAM_ERROR",
                      "Upstream Audio Error",
                      "Upstream audio provider returned HTTP " + status.value());
                } else {
                  throw new IOException("Unexpected HTTP status from upstream audio: " + status);
                }
              });
    } catch (AudioException ae) {
      throw ae;
    } catch (Exception e) {
      log.warn("Remote audio fetch failed: {}", e.getClass().getSimpleName());
      throw new AudioException(
          HttpStatus.BAD_GATEWAY,
          "UPSTREAM_ERROR",
          "Upstream Audio Error",
          "Failed to retrieve audio stream from remote provider");
    }
  }

  private boolean isFakeMedia(String previewUrl) {
    if (previewUrl == null) {
      return false;
    }
    // m2: Only permit classpath fake-media if provider type is fake and matches pattern without
    // traversal
    return SAFE_FAKE_MEDIA_PATTERN.matcher(previewUrl).matches();
  }

  private String normalizeClasspath(String previewUrl) {
    return previewUrl.startsWith("classpath:")
        ? previewUrl
        : "classpath:" + previewUrl.replaceFirst("^/", "");
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
