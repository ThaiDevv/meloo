package com.meloo.generated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.meloo.generated.api.CatalogApi;
import com.meloo.generated.api.GameApi;
import com.meloo.generated.api.IdentityApi;
import com.meloo.generated.model.GuessRequestDto;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

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
      "AC 2 / Cập nhật 24/10: Model sinh ra sử dụng đúng Jackson annotations tương thích Spring Boot 4")
  void generatedModelsUseCompatibleJacksonAnnotations() throws NoSuchMethodException {
    assertThat(GuessRequestDto.class.isAnnotationPresent(JsonTypeName.class)).isTrue();
    assertThat(
            GuessRequestDto.class.getMethod("getTrackId").isAnnotationPresent(JsonProperty.class))
        .isTrue();
  }

  @Test
  @DisplayName(
      "AC 3: Toàn bộ model sinh ra đều có hậu tố Dto, không trùng tên với persistence entities và Spring Data Page")
  void allGeneratedModelsHaveDtoSuffixAndDoNotCollideWithEntities() throws IOException {
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    Resource[] resources = resolver.getResources("classpath*:com/meloo/generated/model/*.class");

    assertThat(resources).isNotEmpty();

    Set<String> sensitiveNames =
        Set.of(
            "Artist",
            "Genre",
            "Playlist",
            "MusicProvider",
            "PlaylistKind",
            "RoundOutcome",
            "MatchStatus",
            "UserKind",
            "UserRole",
            "Page");

    List<String> topLevelClassNames =
        Arrays.stream(resources)
            .map(Resource::getFilename)
            .filter(name -> name != null && name.endsWith(".class"))
            .map(name -> name.substring(0, name.length() - ".class".length()))
            .filter(name -> !name.contains("$") && !name.equals("package-info"))
            .toList();

    assertThat(topLevelClassNames).hasSizeGreaterThanOrEqualTo(30);

    for (String className : topLevelClassNames) {
      assertThat(className)
          .withFailMessage("Model class %s does not end with 'Dto'", className)
          .endsWith("Dto");

      assertThat(sensitiveNames)
          .withFailMessage(
              "Model class %s collides directly with a domain entity or Spring Data Page",
              className)
          .doesNotContain(className);
    }
  }
}
