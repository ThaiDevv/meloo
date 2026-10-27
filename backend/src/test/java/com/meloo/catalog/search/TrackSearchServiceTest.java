package com.meloo.catalog.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meloo.generated.model.FilterOptionsDto;
import com.meloo.generated.model.PlaylistPageDto;
import com.meloo.generated.model.TrackPageDto;
import com.meloo.generated.model.TrackSuggestionDto;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TrackSearchServiceTest {

  private CatalogQueryRepository repository;
  private TrackSearchService service;

  @BeforeEach
  void setUp() {
    repository = mock(CatalogQueryRepository.class);
    service = new TrackSearchService(repository);
  }

  @Test
  @DisplayName("Normalize search query handles Vietnamese accents, 'đ'/'Đ', uppercase, and symbols")
  void normalizeSearchQueryHandlesVietnameseAndSpecialCharacters() {
    assertThat(TrackSearchService.normalizeSearchQuery("Sơn Tùng M-TP")).isEqualTo("son tung m tp");
    assertThat(TrackSearchService.normalizeSearchQuery("Đen Vâu")).isEqualTo("den vau");
    assertThat(TrackSearchService.normalizeSearchQuery("Lạc Trôi (Official MV)"))
        .isEqualTo("lac troi official mv");
    assertThat(TrackSearchService.normalizeSearchQuery("   Chạy   Ngay   Đi... !?   "))
        .isEqualTo("chay ngay di");
    assertThat(TrackSearchService.normalizeSearchQuery("1989 (Taylor's Version)"))
        .isEqualTo("1989 taylor s version");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "   ", "\t\n", "a", "Z", "1", "!", " ?", " đ ", " . "})
  @DisplayName("Query with length < 2 after normalization returns empty page without DB query")
  void shortOrEmptyQueriesReturnEmpty(String query) {
    TrackPageDto result = service.searchTracks(query, 0, 10);

    assertThat(result.getItems()).isEmpty();
    assertThat(result.getTotalItems()).isZero();
    assertThat(result.getTotalPages()).isZero();
    assertThat(result.getPage()).isZero();
    assertThat(result.getSize()).isEqualTo(10);
    verifyNoInteractions(repository);
  }

  @Test
  @DisplayName("Null query returns empty page without DB query")
  void nullQueryReturnsEmpty() {
    TrackPageDto result = service.searchTracks(null, 0, 10);

    assertThat(result.getItems()).isEmpty();
    assertThat(result.getTotalItems()).isZero();
    verifyNoInteractions(repository);
  }

  @Test
  @DisplayName("Valid query delegates to repository with normalized string")
  void validQueryDelegatesToRepository() {
    TrackPageDto mockPage =
        new TrackPageDto()
            .page(0)
            .size(10)
            .totalItems(1L)
            .totalPages(1)
            .items(
                List.of(
                    new TrackSuggestionDto()
                        .id(UUID.randomUUID())
                        .title("Lạc Trôi")
                        .artistName("Sơn Tùng M-TP")));

    when(repository.searchTracks(eq("lac troi"), eq(0), eq(10))).thenReturn(mockPage);

    TrackPageDto result = service.searchTracks("  Lạc   Trôi!  ", 0, 10);

    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getItems().getFirst().getTitle()).isEqualTo("Lạc Trôi");
    verify(repository).searchTracks("lac troi", 0, 10);
  }

  @Test
  @DisplayName("Paging bounds are constrained to valid ranges")
  void pagingBoundsConstrained() {
    when(repository.searchTracks(anyString(), anyInt(), anyInt()))
        .thenReturn(new TrackPageDto().items(List.of()));

    service.searchTracks("son tung", -5, 100);
    // page clamped to 0, size clamped to 50
    verify(repository).searchTracks("son tung", 0, 50);

    service.searchTracks("son tung", 2, 0);
    // size clamped to min 1
    verify(repository).searchTracks("son tung", 2, 1);
  }

  @Test
  @DisplayName("listPlaylists delegates to repository")
  void listPlaylistsDelegates() {
    PlaylistPageDto mockPage = new PlaylistPageDto().items(List.of());
    when(repository.listPlaylists(0, 10)).thenReturn(mockPage);

    PlaylistPageDto result = service.listPlaylists(null, null);

    assertThat(result).isSameAs(mockPage);
    verify(repository).listPlaylists(0, 10);
  }

  @Test
  @DisplayName("getFilters delegates to repository")
  void getFiltersDelegates() {
    FilterOptionsDto mockFilters = new FilterOptionsDto();
    when(repository.getFilters()).thenReturn(mockFilters);

    FilterOptionsDto result = service.getFilters();

    assertThat(result).isSameAs(mockFilters);
    verify(repository).getFilters();
  }
}
