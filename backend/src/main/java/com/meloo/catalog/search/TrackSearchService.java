package com.meloo.catalog.search;

import com.meloo.catalog.importing.TitleNormalizer;
import com.meloo.generated.model.FilterOptionsDto;
import com.meloo.generated.model.PlaylistPageDto;
import com.meloo.generated.model.TrackPageDto;
import java.util.Collections;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TrackSearchService {

  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}\\s]+");
  private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");

  private final CatalogQueryRepository catalogQueryRepository;

  public TrackSearchService(CatalogQueryRepository catalogQueryRepository) {
    this.catalogQueryRepository = catalogQueryRepository;
  }

  /**
   * Searches tracks by autocomplete query with ranking: Prefix match > Word boundary match >
   * Trigram similarity.
   *
   * <p>Returns empty result if query length < 2 characters after normalization.
   */
  public TrackPageDto searchTracks(
      @Nullable String query, @Nullable Integer page, @Nullable Integer size) {
    int p = page == null ? 0 : Math.max(0, page);
    int s = size == null ? 10 : Math.max(1, Math.min(size, 50));

    String normalized = normalizeSearchQuery(query);
    if (normalized.length() < 2) {
      return new TrackPageDto()
          .page(p)
          .size(s)
          .totalItems(0L)
          .totalPages(0)
          .items(Collections.emptyList());
    }

    return catalogQueryRepository.searchTracks(normalized, p, s);
  }

  /** Lists playlists with active track counts. */
  public PlaylistPageDto listPlaylists(@Nullable Integer page, @Nullable Integer size) {
    int p = page == null ? 0 : Math.max(0, page);
    int s = size == null ? 10 : Math.max(1, Math.min(size, 50));
    return catalogQueryRepository.listPlaylists(p, s);
  }

  /** Retrieves practice filter options populated with values with at least one enabled track. */
  public FilterOptionsDto getFilters() {
    return catalogQueryRepository.getFilters();
  }

  /**
   * Normalizes search query by converting to lowercase, removing diacritics (including Vietnamese
   * đ/Đ), replacing punctuation with spaces, and trimming whitespace.
   */
  public static String normalizeSearchQuery(@Nullable String query) {
    if (query == null || query.isBlank()) {
      return "";
    }
    String stripped = TitleNormalizer.stripDiacritics(query.trim().toLowerCase(Locale.ROOT));
    String cleaned = NON_ALPHANUMERIC.matcher(stripped).replaceAll(" ");
    return MULTI_WHITESPACE.matcher(cleaned).replaceAll(" ").trim();
  }
}
