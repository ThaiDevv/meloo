package com.meloo.generated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meloo.generated.api.CatalogApi;
import com.meloo.generated.api.GameApi;
import com.meloo.generated.api.IdentityApi;
import com.meloo.generated.model.ArtistDto;
import com.meloo.generated.model.GenreDto;
import com.meloo.generated.model.MatchStatusDto;
import com.meloo.generated.model.MusicProviderDto;
import com.meloo.generated.model.PageDto;
import com.meloo.generated.model.PlaylistDto;
import com.meloo.generated.model.PlaylistKindDto;
import com.meloo.generated.model.RoundOutcomeDto;
import com.meloo.generated.model.UserKindDto;
import com.meloo.generated.model.UserRoleDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeneratedContractModelsTest {

  @Test
  @DisplayName(
      "AC 1: Sinh ra IdentityApi, CatalogApi, GameApi riêng biệt theo tag; không còn ApiApi")
  void openApiGeneratorGeneratesTaggedApisWithoutApiApi() {
    assertThat(IdentityApi.class.isInterface()).isTrue();
    assertThat(CatalogApi.class.isInterface()).isTrue();
    assertThat(GameApi.class.isInterface()).isTrue();

    assertThatThrownBy(() -> Class.forName("com.meloo.generated.api.ApiApi"))
        .isInstanceOf(ClassNotFoundException.class);
  }

  @Test
  @DisplayName(
      "AC 3: Model DTO có hậu tố Dto, không trùng tên với persistence entities và Spring Data Page")
  void modelNamesDoNotCollideWithPersistenceEntitiesAndSpringDataPage() {
    // 1. Artist entity vs ArtistDto
    Class<?> entityArtist = com.meloo.catalog.persistence.Artist.class;
    Class<?> dtoArtist = ArtistDto.class;
    assertThat(dtoArtist.getSimpleName()).isNotEqualTo(entityArtist.getSimpleName());

    // 2. Genre entity vs GenreDto
    Class<?> entityGenre = com.meloo.catalog.persistence.Genre.class;
    Class<?> dtoGenre = GenreDto.class;
    assertThat(dtoGenre.getSimpleName()).isNotEqualTo(entityGenre.getSimpleName());

    // 3. Playlist entity vs PlaylistDto
    Class<?> entityPlaylist = com.meloo.catalog.persistence.Playlist.class;
    Class<?> dtoPlaylist = PlaylistDto.class;
    assertThat(dtoPlaylist.getSimpleName()).isNotEqualTo(entityPlaylist.getSimpleName());

    // 4. MusicProvider enum vs MusicProviderDto
    Class<?> enumMusicProvider = com.meloo.catalog.persistence.MusicProvider.class;
    Class<?> dtoMusicProvider = MusicProviderDto.class;
    assertThat(dtoMusicProvider.getSimpleName()).isNotEqualTo(enumMusicProvider.getSimpleName());

    // 5. PlaylistKind enum vs PlaylistKindDto
    Class<?> enumPlaylistKind = com.meloo.catalog.persistence.PlaylistKind.class;
    Class<?> dtoPlaylistKind = PlaylistKindDto.class;
    assertThat(dtoPlaylistKind.getSimpleName()).isNotEqualTo(enumPlaylistKind.getSimpleName());

    // 6. RoundOutcome enum vs RoundOutcomeDto
    Class<?> enumRoundOutcome = com.meloo.progression.persistence.RoundOutcome.class;
    Class<?> dtoRoundOutcome = RoundOutcomeDto.class;
    assertThat(dtoRoundOutcome.getSimpleName()).isNotEqualTo(enumRoundOutcome.getSimpleName());

    // 7. MatchStatus enum vs MatchStatusDto
    Class<?> enumMatchStatus = com.meloo.progression.persistence.MatchStatus.class;
    Class<?> dtoMatchStatus = MatchStatusDto.class;
    assertThat(dtoMatchStatus.getSimpleName()).isNotEqualTo(enumMatchStatus.getSimpleName());

    // 8. UserKind enum vs UserKindDto
    Class<?> enumUserKind = com.meloo.identity.persistence.UserKind.class;
    Class<?> dtoUserKind = UserKindDto.class;
    assertThat(dtoUserKind.getSimpleName()).isNotEqualTo(enumUserKind.getSimpleName());

    // 9. UserRole enum vs UserRoleDto
    Class<?> enumUserRole = com.meloo.identity.persistence.UserRole.class;
    Class<?> dtoUserRole = UserRoleDto.class;
    assertThat(dtoUserRole.getSimpleName()).isNotEqualTo(enumUserRole.getSimpleName());

    // 10. Spring Data Page vs PageDto
    Class<?> springDataPage = org.springframework.data.domain.Page.class;
    Class<?> dtoPage = PageDto.class;
    assertThat(dtoPage.getSimpleName()).isNotEqualTo(springDataPage.getSimpleName());
  }
}
