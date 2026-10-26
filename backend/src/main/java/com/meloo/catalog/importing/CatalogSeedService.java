package com.meloo.catalog.importing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.meloo.catalog.persistence.Artist;
import com.meloo.catalog.persistence.ArtistRepository;
import com.meloo.catalog.persistence.Genre;
import com.meloo.catalog.persistence.GenreRepository;
import com.meloo.catalog.persistence.Playlist;
import com.meloo.catalog.persistence.PlaylistKind;
import com.meloo.catalog.persistence.PlaylistRepository;
import com.meloo.catalog.persistence.PlaylistTrack;
import com.meloo.catalog.persistence.PlaylistTrackId;
import com.meloo.catalog.persistence.PlaylistTrackRepository;
import com.meloo.catalog.persistence.Track;
import com.meloo.catalog.persistence.TrackRepository;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class CatalogSeedService {

  private static final Logger log = LoggerFactory.getLogger(CatalogSeedService.class);

  private final CatalogImportProperties properties;
  private final ResourceLoader resourceLoader;
  private final ObjectMapper objectMapper;
  private final GenreRepository genreRepository;
  private final PlaylistRepository playlistRepository;
  private final PlaylistTrackRepository playlistTrackRepository;
  private final ArtistRepository artistRepository;
  private final TrackRepository trackRepository;
  private final EntityManager entityManager;
  private final TransactionTemplate transactionTemplate;

  public CatalogSeedService(
      CatalogImportProperties properties,
      ResourceLoader resourceLoader,
      ObjectMapper objectMapper,
      GenreRepository genreRepository,
      PlaylistRepository playlistRepository,
      PlaylistTrackRepository playlistTrackRepository,
      ArtistRepository artistRepository,
      TrackRepository trackRepository,
      EntityManager entityManager,
      TransactionTemplate transactionTemplate) {
    this.properties = properties;
    this.resourceLoader = resourceLoader;
    this.objectMapper = objectMapper;
    this.genreRepository = genreRepository;
    this.playlistRepository = playlistRepository;
    this.playlistTrackRepository = playlistTrackRepository;
    this.artistRepository = artistRepository;
    this.trackRepository = trackRepository;
    this.entityManager = entityManager;
    this.transactionTemplate = transactionTemplate;
  }

  public record CatalogSeedResult(
      int genresCount, int playlistsCount, int tracksCount, int playlistTracksCount) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SeedCatalogDto(
      @Nullable List<SeedGenreDto> genres,
      @Nullable List<SeedPlaylistDto> playlists,
      @Nullable List<SeedTrackDto> tracks) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SeedGenreDto(String code, String nameVi, String nameEn) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SeedPlaylistDto(
      String code,
      String nameVi,
      String nameEn,
      @Nullable String kind,
      @Nullable Boolean enabled) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SeedTrackDto(
      @Nullable String provider,
      String providerTrackId,
      String title,
      String artistName,
      @Nullable String providerArtistId,
      @Nullable String genreCode,
      @Nullable String countryCode,
      @Nullable Short releaseYear,
      @Nullable Integer popularityRank,
      @Nullable String previewUrl,
      @Nullable String coverUrl,
      @Nullable List<String> playlistCodes) {}

  /** Seeds the catalog from the configured seed JSON resource. */
  public CatalogSeedResult seedCatalog() {
    return seedCatalogFromResource(properties.seed().seedPath());
  }

  /** Seeds the catalog from a specific resource path (e.g. "classpath:seed/catalog-seed.json"). */
  public CatalogSeedResult seedCatalogFromResource(String resourceLocation) {
    Resource resource = resourceLoader.getResource(resourceLocation);
    if (!resource.exists()) {
      log.warn("Catalog seed resource not found: {}", resourceLocation);
      return new CatalogSeedResult(0, 0, 0, 0);
    }

    try (InputStream is = resource.getInputStream()) {
      SeedCatalogDto catalogDto = objectMapper.readValue(is, SeedCatalogDto.class);
      return seedFromDto(catalogDto);
    } catch (IOException e) {
      log.error("Failed to read catalog seed resource: {}", resourceLocation, e);
      throw new IllegalStateException("Failed to parse catalog seed JSON", e);
    }
  }

  /** Seeds database entities within an explicit TransactionTemplate boundary. */
  public CatalogSeedResult seedFromDto(SeedCatalogDto dto) {
    return transactionTemplate.execute(
        status -> {
          Map<String, UUID> genreIdByCode = seedGenres(dto.genres());
          Map<String, UUID> playlistIdByCode = seedPlaylists(dto.playlists());
          int[] trackStats = seedTracks(dto.tracks(), genreIdByCode, playlistIdByCode);

          log.info(
              "Catalog seed completed: {} genres, {} playlists, {} tracks seeded, {} playlist links",
              genreIdByCode.size(),
              playlistIdByCode.size(),
              trackStats[0],
              trackStats[1]);

          return new CatalogSeedResult(
              genreIdByCode.size(), playlistIdByCode.size(), trackStats[0], trackStats[1]);
        });
  }

  private Map<String, UUID> seedGenres(@Nullable List<SeedGenreDto> genreDtos) {
    Map<String, UUID> map = new HashMap<>();
    if (genreDtos == null) {
      return map;
    }

    for (SeedGenreDto g : genreDtos) {
      if (g.code() == null || g.code().isBlank()) {
        continue;
      }
      List<Genre> existing =
          entityManager
              .createQuery("select g from Genre g where g.code = :code", Genre.class)
              .setParameter("code", g.code().trim().toLowerCase(Locale.ROOT))
              .getResultList();

      if (!existing.isEmpty()) {
        map.put(g.code().trim().toLowerCase(Locale.ROOT), existing.getFirst().getId());
      } else {
        Genre newGenre =
            Genre.builder()
                .id(UUID.randomUUID())
                .code(g.code().trim().toLowerCase(Locale.ROOT))
                .nameVi(g.nameVi())
                .nameEn(g.nameEn())
                .build();
        Genre saved = genreRepository.save(newGenre);
        map.put(saved.getCode(), saved.getId());
      }
    }
    return map;
  }

  private Map<String, UUID> seedPlaylists(@Nullable List<SeedPlaylistDto> playlistDtos) {
    Map<String, UUID> map = new HashMap<>();
    if (playlistDtos == null) {
      return map;
    }

    for (SeedPlaylistDto p : playlistDtos) {
      if (p.code() == null || p.code().isBlank()) {
        continue;
      }
      String code = p.code().trim().toLowerCase(Locale.ROOT);
      List<Playlist> existing =
          entityManager
              .createQuery("select p from Playlist p where p.code = :code", Playlist.class)
              .setParameter("code", code)
              .getResultList();

      if (!existing.isEmpty()) {
        map.put(code, existing.getFirst().getId());
      } else {
        PlaylistKind kind = PlaylistKind.CURATED;
        if (p.kind() != null && p.kind().equalsIgnoreCase("STANDARD")) {
          kind = PlaylistKind.STANDARD;
        }

        Playlist newPlaylist =
            Playlist.builder()
                .id(UUID.randomUUID())
                .code(code)
                .nameVi(p.nameVi())
                .nameEn(p.nameEn())
                .kind(kind)
                .enabled(p.enabled() == null || p.enabled())
                .build();
        Playlist saved = playlistRepository.save(newPlaylist);
        map.put(saved.getCode(), saved.getId());
      }
    }
    return map;
  }

  private int[] seedTracks(
      @Nullable List<SeedTrackDto> trackDtos,
      Map<String, UUID> genreIdByCode,
      Map<String, UUID> playlistIdByCode) {
    if (trackDtos == null) {
      return new int[] {0, 0};
    }

    Map<UUID, Integer> playlistPositionCounter = new HashMap<>();
    int tracksSeeded = 0;
    int playlistLinks = 0;

    for (SeedTrackDto t : trackDtos) {
      // Skip if previewUrl is blank (AC: bỏ bài không có preview)
      if (t.previewUrl() == null || t.previewUrl().isBlank()) {
        continue;
      }

      com.meloo.catalog.persistence.MusicProvider provider = resolveProvider(t.provider());

      // Deduplication check
      Optional<Track> existingTrackOpt = findTrackByProvider(provider, t.providerTrackId());
      Track track;
      if (existingTrackOpt.isPresent()) {
        track = existingTrackOpt.get();
        if (track.getCoverUrl() == null && t.coverUrl() != null && !t.coverUrl().isBlank()) {
          track.setCoverUrl(t.coverUrl());
          track = trackRepository.save(track);
        }
      } else {
        Artist artist = findOrCreateArtist(provider, t.artistName(), t.providerArtistId());
        UUID genreId =
            (t.genreCode() != null)
                ? genreIdByCode.get(t.genreCode().trim().toLowerCase(Locale.ROOT))
                : null;

        String country = t.countryCode();
        if (country != null) {
          country = country.trim().toUpperCase(Locale.ROOT);
          if (country.length() > 2) {
            country = country.substring(0, 2);
          }
        }

        Track newTrack =
            Track.builder()
                .id(UUID.randomUUID())
                .provider(provider)
                .providerTrackId(t.providerTrackId())
                .title(t.title())
                .titleNormalized(TitleNormalizer.normalizeTitle(t.title()))
                .primaryArtistId(artist.getId())
                .genreId(genreId)
                .countryCode(country)
                .releaseYear(t.releaseYear())
                .popularityRank(t.popularityRank())
                .enabled(true)
                .coverUrl(t.coverUrl())
                .build();

        track = trackRepository.save(newTrack);
        tracksSeeded++;
      }

      // Link to curated playlists
      if (t.playlistCodes() != null && !t.playlistCodes().isEmpty()) {
        for (String pCode : t.playlistCodes()) {
          UUID playlistId = playlistIdByCode.get(pCode.trim().toLowerCase(Locale.ROOT));
          if (playlistId != null) {
            PlaylistTrackId id = new PlaylistTrackId(playlistId, track.getId());
            if (!playlistTrackRepository.existsById(id)) {
              // Ensure position continues after existing playlist tracks
              int nextPos =
                  playlistPositionCounter.computeIfAbsent(
                          playlistId,
                          pid -> {
                            Integer maxPos =
                                entityManager
                                    .createQuery(
                                        "select max(pt.position) from PlaylistTrack pt where pt.playlistId = :pid",
                                        Integer.class)
                                    .setParameter("pid", pid)
                                    .getSingleResult();
                            return (maxPos == null) ? 0 : maxPos;
                          })
                      + 1;
              playlistPositionCounter.put(playlistId, nextPos);

              PlaylistTrack link =
                  PlaylistTrack.builder()
                      .playlistId(playlistId)
                      .trackId(track.getId())
                      .position(nextPos)
                      .build();
              playlistTrackRepository.save(link);
              playlistLinks++;
            }
          }
        }
      }
    }

    return new int[] {tracksSeeded, playlistLinks};
  }

  private com.meloo.catalog.persistence.MusicProvider resolveProvider(
      @Nullable String providerStr) {
    if (providerStr == null || providerStr.isBlank() || providerStr.equalsIgnoreCase("deezer")) {
      return com.meloo.catalog.persistence.MusicProvider.DEEZER;
    }
    if (providerStr.equalsIgnoreCase("itunes")) {
      return com.meloo.catalog.persistence.MusicProvider.ITUNES;
    }
    throw new IllegalArgumentException("Unsupported music provider in seed: " + providerStr);
  }

  private Optional<Track> findTrackByProvider(
      com.meloo.catalog.persistence.MusicProvider provider, String providerTrackId) {
    List<Track> tracks =
        entityManager
            .createQuery(
                "select t from Track t where t.provider = :provider and t.providerTrackId = :pid",
                Track.class)
            .setParameter("provider", provider)
            .setParameter("pid", providerTrackId)
            .getResultList();
    return tracks.isEmpty() ? Optional.empty() : Optional.of(tracks.getFirst());
  }

  private Artist findOrCreateArtist(
      com.meloo.catalog.persistence.MusicProvider provider,
      String artistName,
      @Nullable String providerArtistId) {
    String pid =
        (providerArtistId != null && !providerArtistId.isBlank())
            ? providerArtistId
            : "art-" + TitleNormalizer.normalizeArtist(artistName);

    List<Artist> artists =
        entityManager
            .createQuery(
                "select a from Artist a where a.provider = :provider and a.providerArtistId = :pid",
                Artist.class)
            .setParameter("provider", provider)
            .setParameter("pid", pid)
            .getResultList();

    if (!artists.isEmpty()) {
      return artists.getFirst();
    }

    Artist newArtist =
        Artist.builder()
            .id(UUID.randomUUID())
            .name(artistName != null ? artistName : "Unknown")
            .nameNormalized(TitleNormalizer.normalizeArtist(artistName))
            .provider(provider)
            .providerArtistId(pid)
            .build();

    return artistRepository.save(newArtist);
  }
}
