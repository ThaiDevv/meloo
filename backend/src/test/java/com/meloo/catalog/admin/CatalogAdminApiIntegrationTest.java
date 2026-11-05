package com.meloo.catalog.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.meloo.IntegrationTest;
import com.meloo.catalog.api.AdminChange;
import com.meloo.catalog.api.AdminImportResult;
import com.meloo.catalog.api.AdminPage;
import com.meloo.catalog.api.AdminPlaylistView;
import com.meloo.catalog.api.AdminTrackQuery;
import com.meloo.catalog.api.AdminTrackView;
import com.meloo.catalog.api.CatalogAdminApi;
import com.meloo.catalog.api.CatalogAdminException;
import com.meloo.catalog.api.CatalogAdminException.Reason;
import com.meloo.catalog.api.CatalogService;
import com.meloo.catalog.api.TrackInfo;
import com.meloo.catalog.api.TrackMetadataUpdate;
import com.meloo.catalog.persistence.Artist;
import com.meloo.catalog.persistence.ArtistRepository;
import com.meloo.catalog.persistence.Genre;
import com.meloo.catalog.persistence.GenreRepository;
import com.meloo.catalog.persistence.MusicProvider;
import com.meloo.catalog.persistence.Playlist;
import com.meloo.catalog.persistence.PlaylistKind;
import com.meloo.catalog.persistence.PlaylistRepository;
import com.meloo.catalog.persistence.PlaylistTrack;
import com.meloo.catalog.persistence.PlaylistTrackRepository;
import com.meloo.catalog.persistence.Track;
import com.meloo.catalog.persistence.TrackRepository;
import com.meloo.catalog.provider.ProviderTrack;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class CatalogAdminApiIntegrationTest extends IntegrationTest {

  @Autowired private CatalogAdminApi admin;
  @Autowired private CatalogService catalogService;
  @Autowired private TrackRepository trackRepository;
  @Autowired private ArtistRepository artistRepository;
  @Autowired private GenreRepository genreRepository;
  @Autowired private PlaylistRepository playlistRepository;
  @Autowired private PlaylistTrackRepository playlistTrackRepository;

  @MockitoBean private com.meloo.catalog.provider.MusicProvider musicProvider;

  private Artist artist;
  private Genre genreVpop;
  private Genre genreKpop;
  private Playlist standard;

  @BeforeEach
  void setUp() {
    cleanDatabase();
    artist =
        artistRepository.saveAndFlush(
            Artist.builder()
                .id(UUID.randomUUID())
                .name("Son Tung")
                .nameNormalized("son tung")
                .provider(MusicProvider.DEEZER)
                .providerArtistId("art-st")
                .build());
    genreVpop = genre("vpop");
    genreKpop = genre("kpop");
    standard =
        playlistRepository.saveAndFlush(
            Playlist.builder()
                .id(UUID.randomUUID())
                .code("standard")
                .nameVi("Chuẩn")
                .nameEn("Standard")
                .kind(PlaylistKind.STANDARD)
                .build());
  }

  @AfterEach
  void tearDown() {
    cleanDatabase();
  }

  private void cleanDatabase() {
    playlistTrackRepository.deleteAll();
    trackRepository.deleteAll();
    playlistRepository.deleteAll();
    artistRepository.deleteAll();
    genreRepository.deleteAll();
  }

  private Genre genre(String code) {
    return genreRepository.saveAndFlush(
        Genre.builder().id(UUID.randomUUID()).code(code).nameVi(code).nameEn(code).build());
  }

  private Track track(String title, boolean enabled) {
    return trackRepository.saveAndFlush(
        Track.builder()
            .id(UUID.randomUUID())
            .provider(MusicProvider.DEEZER)
            .providerTrackId("dz-" + UUID.randomUUID())
            .title(title)
            .titleNormalized(title.toLowerCase())
            .primaryArtistId(artist.getId())
            .genreId(genreVpop.getId())
            .countryCode("VN")
            .releaseYear((short) 2019)
            .enabled(enabled)
            .build());
  }

  private List<UUID> tracks(int n) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      ids.add(track("Song " + i, true).getId());
    }
    return ids;
  }

  private void addToStandard(UUID trackId, int position) {
    playlistTrackRepository.saveAndFlush(
        PlaylistTrack.builder()
            .playlistId(standard.getId())
            .trackId(trackId)
            .position(position)
            .build());
  }

  @Test
  @DisplayName("disabling a track removes it from track selection and returns before/after")
  void disabledTrackIsNotSelected() {
    Track only = track("Only Song", true);
    addToStandard(only.getId(), 0);
    assertThat(catalogService.selectRandomTracksByPlaylistCode("standard", 5))
        .extracting(TrackInfo::id)
        .containsExactly(only.getId());

    AdminChange<AdminTrackView> change = admin.setTrackEnabled(only.getId(), false, "bad preview");

    assertThat(change.before()).isNotNull();
    assertThat(change.before().enabled()).isTrue();
    assertThat(change.after()).isNotNull();
    assertThat(change.after().enabled()).isFalse();
    assertThat(change.after().disabledReason()).isEqualTo("bad preview");
    assertThat(catalogService.selectRandomTracksByPlaylistCode("standard", 5)).isEmpty();
    assertThat(catalogService.selectRandomTracks(null, 5)).isEmpty();

    AdminChange<AdminTrackView> back = admin.setTrackEnabled(only.getId(), true, null);
    assertThat(back.after().enabled()).isTrue();
    assertThat(back.after().disabledReason()).isNull();
    assertThat(catalogService.selectRandomTracks(null, 5)).hasSize(1);
  }

  @Test
  @DisplayName("updating metadata returns the exact values before and after")
  void updateMetadataReturnsBeforeAndAfter() {
    Track t = track("Song", true);

    AdminChange<AdminTrackView> change =
        admin.updateTrackMetadata(
            t.getId(), new TrackMetadataUpdate(genreKpop.getId(), "kr", (short) 2023));

    AdminTrackView before = change.before();
    AdminTrackView after = change.after();
    assertThat(before).isNotNull();
    assertThat(after).isNotNull();
    assertThat(before.genreCode()).isEqualTo("vpop");
    assertThat(before.countryCode()).isEqualTo("VN");
    assertThat(before.releaseYear()).isEqualTo((short) 2019);
    assertThat(after.genreCode()).isEqualTo("kpop");
    assertThat(after.countryCode()).isEqualTo("KR");
    assertThat(after.releaseYear()).isEqualTo((short) 2023);
  }

  @Test
  @DisplayName("invalid metadata and unknown track are rejected")
  void invalidMetadataRejected() {
    Track t = track("Song", true);
    assertThatThrownBy(
            () ->
                admin.updateTrackMetadata(
                    t.getId(), new TrackMetadataUpdate(null, "VNM", (short) 2020)))
        .isInstanceOfSatisfying(
            CatalogAdminException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.INVALID_ARGUMENT));
    assertThatThrownBy(
            () ->
                admin.updateTrackMetadata(
                    t.getId(), new TrackMetadataUpdate(UUID.randomUUID(), null, null)))
        .isInstanceOfSatisfying(
            CatalogAdminException.class, e -> assertThat(e.reason()).isEqualTo(Reason.NOT_FOUND));
    assertThatThrownBy(() -> admin.setTrackEnabled(UUID.randomUUID(), false, null))
        .isInstanceOfSatisfying(
            CatalogAdminException.class, e -> assertThat(e.reason()).isEqualTo(Reason.NOT_FOUND));
  }

  @Test
  @DisplayName("searchTracks filters by text, enabled state and genre with paging")
  void searchTracksFilters() {
    Track a = track("Hello World", true);
    track("Hello Again", false);
    track("Other", true);
    admin.updateTrackMetadata(
        a.getId(), new TrackMetadataUpdate(genreKpop.getId(), "KR", (short) 2023));

    AdminPage<AdminTrackView> all =
        admin.searchTracks(new AdminTrackQuery(null, null, null, null, null, 0, 2));
    assertThat(all.total()).isEqualTo(3);
    assertThat(all.items()).hasSize(2);

    AdminPage<AdminTrackView> hello =
        admin.searchTracks(new AdminTrackQuery("hello", null, null, null, null, 0, 10));
    assertThat(hello.items()).hasSize(2);

    AdminPage<AdminTrackView> disabled =
        admin.searchTracks(new AdminTrackQuery("hello", false, null, null, null, 0, 10));
    assertThat(disabled.items()).extracting(AdminTrackView::title).containsExactly("Hello Again");

    AdminPage<AdminTrackView> kpop =
        admin.searchTracks(new AdminTrackQuery(null, null, "kpop", "kr", 2020, 0, 10));
    assertThat(kpop.items()).extracting(AdminTrackView::id).containsExactly(a.getId());
  }

  @Test
  @DisplayName("a curated playlist without enough valid tracks is rejected")
  void playlistNeedsEnoughValidTracks() {
    int min = admin.minPlaylistTracks();
    List<UUID> enough = tracks(min);

    List<UUID> tooFew = enough.subList(0, min - 1);
    assertThatThrownBy(() -> admin.createPlaylist("small", "Nhỏ", "Small", tooFew))
        .isInstanceOfSatisfying(
            CatalogAdminException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.PLAYLIST_NOT_ENOUGH_TRACKS));

    List<UUID> withDisabled = new ArrayList<>(tooFew);
    withDisabled.add(track("Off", false).getId());
    assertThatThrownBy(() -> admin.createPlaylist("small", "Nhỏ", "Small", withDisabled))
        .isInstanceOfSatisfying(
            CatalogAdminException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.INVALID_ARGUMENT));

    List<UUID> duplicated = new ArrayList<>(tooFew);
    duplicated.add(tooFew.getFirst());
    assertThatThrownBy(() -> admin.createPlaylist("small", "Nhỏ", "Small", duplicated))
        .isInstanceOfSatisfying(
            CatalogAdminException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.PLAYLIST_NOT_ENOUGH_TRACKS));
    assertThat(playlistRepository.findAll()).hasSize(1);

    AdminChange<AdminPlaylistView> created = admin.createPlaylist("ok", "Tốt", "Ok", enough);
    assertThat(created.before()).isNull();
    assertThat(created.after()).isNotNull();
    assertThat(created.after().enabledTrackCount()).isEqualTo(min);
    assertThat(created.after().trackIds()).containsExactlyElementsOf(enough);
    assertThat(created.after().curated()).isTrue();
  }

  @Test
  @DisplayName("playlist edit lifecycle: add, remove, rename, delete")
  void playlistLifecycle() {
    int min = admin.minPlaylistTracks();
    List<UUID> ids = tracks(min + 2);
    UUID id = admin.createPlaylist("mix", "Mix", "Mix", ids.subList(0, min)).after().id();

    AdminChange<AdminPlaylistView> added =
        admin.addTracksToPlaylist(id, ids.subList(min - 1, min + 2));
    assertThat(added.before().trackIds()).hasSize(min);
    assertThat(added.after().trackIds()).containsExactlyElementsOf(ids);

    AdminChange<AdminPlaylistView> removed =
        admin.removeTracksFromPlaylist(id, Set.of(ids.getFirst(), ids.get(1)));
    assertThat(removed.after().trackIds()).containsExactlyElementsOf(ids.subList(2, ids.size()));

    assertThatThrownBy(() -> admin.removeTracksFromPlaylist(id, Set.of(ids.get(2), ids.get(3))))
        .isInstanceOfSatisfying(
            CatalogAdminException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.PLAYLIST_NOT_ENOUGH_TRACKS));
    assertThat(catalogService.findPlaylistById(id))
        .get()
        .satisfies(p -> assertThat(p.enabledTrackCount()).isEqualTo(min));

    AdminChange<AdminPlaylistView> renamed = admin.updatePlaylist(id, "Tên mới", "New name", true);
    assertThat(renamed.before().nameEn()).isEqualTo("Mix");
    assertThat(renamed.after().nameEn()).isEqualTo("New name");

    AdminChange<AdminPlaylistView> deleted = admin.deletePlaylist(id);
    assertThat(deleted.before()).isNotNull();
    assertThat(deleted.after()).isNull();
    assertThat(catalogService.findPlaylistById(id)).isEmpty();
  }

  @Test
  @DisplayName("standard playlist is read-only and disabled tracks cannot be added")
  void standardPlaylistProtected() {
    assertThatThrownBy(() -> admin.deletePlaylist(standard.getId()))
        .isInstanceOfSatisfying(
            CatalogAdminException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.INVALID_ARGUMENT));

    int min = admin.minPlaylistTracks();
    UUID id = admin.createPlaylist("mix", "Mix", "Mix", tracks(min)).after().id();
    UUID off = track("Off", false).getId();
    assertThatThrownBy(() -> admin.addTracksToPlaylist(id, List.of(off)))
        .isInstanceOfSatisfying(
            CatalogAdminException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.INVALID_ARGUMENT));
    assertThatThrownBy(() -> admin.createPlaylist("mix", "Mix", "Mix", tracks(min)))
        .isInstanceOfSatisfying(
            CatalogAdminException.class, e -> assertThat(e.reason()).isEqualTo(Reason.CONFLICT));
  }

  @Test
  @DisplayName("disabling tracks drops a curated playlist below the minimum for new matches")
  void disabledTracksReduceEnabledCount() {
    int min = admin.minPlaylistTracks();
    List<UUID> ids = tracks(min);
    UUID id = admin.createPlaylist("mix", "Mix", "Mix", ids).after().id();
    admin.setTrackEnabled(ids.getFirst(), false, null);

    AdminPlaylistView view =
        admin.listPlaylists().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    assertThat(view.enabledTrackCount()).isEqualTo(min - 1);
    assertThat(catalogService.selectRandomTracksByPlaylist(id, min)).hasSize(min - 1);
    // re-enabling the playlist is refused while it has too few valid tracks
    assertThatThrownBy(() -> admin.updatePlaylist(id, "Mix", "Mix", true))
        .isInstanceOfSatisfying(
            CatalogAdminException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.PLAYLIST_NOT_ENOUGH_TRACKS));
  }

  @Test
  @DisplayName("import by query and by provider id reports created tracks and duplicates")
  void importFromProvider() {
    when(musicProvider.getProviderName()).thenReturn("deezer");
    ProviderTrack pt =
        ProviderTrack.builder()
            .providerTrackId("dz-import-1")
            .title("Imported Song")
            .artistName("Imported Artist")
            .artistId("dz-art-1")
            .previewUrl("https://example.com/p.mp3")
            .build();
    ProviderTrack noPreview =
        ProviderTrack.builder()
            .providerTrackId("dz-import-2")
            .title("No Preview")
            .artistName("Imported Artist")
            .artistId("dz-art-1")
            .build();
    when(musicProvider.search("imported", 10)).thenReturn(List.of(pt, noPreview));
    when(musicProvider.getTrack("dz-import-1")).thenReturn(Optional.of(pt));
    when(musicProvider.getTrack("missing")).thenReturn(Optional.empty());

    AdminImportResult first = admin.importByQuery("imported", 10, genreVpop.getId(), "vn");
    assertThat(first.importedCount()).isEqualTo(1);
    assertThat(first.skippedNoPreviewCount()).isEqualTo(1);
    assertThat(first.tracks()).extracting(AdminTrackView::title).containsExactly("Imported Song");
    assertThat(first.tracks().getFirst().genreCode()).isEqualTo("vpop");

    AdminImportResult again = admin.importByProviderTrackId("dz-import-1", null, null);
    assertThat(again.importedCount()).isZero();
    assertThat(again.duplicateCount()).isEqualTo(1);

    assertThatThrownBy(() -> admin.importByProviderTrackId("missing", null, null))
        .isInstanceOfSatisfying(
            CatalogAdminException.class, e -> assertThat(e.reason()).isEqualTo(Reason.NOT_FOUND));
  }
}
