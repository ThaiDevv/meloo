package com.meloo.audio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meloo.audio.config.AudioProperties;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DeezerAudioSourceResolverTest {

  private static final String BASE_URL = "https://api.deezer.com";

  private AudioProperties.DeezerProperties createProperties() {
    return new AudioProperties.DeezerProperties(
        BASE_URL,
        Duration.ofSeconds(2),
        3,
        Duration.ofMillis(10),
        new AudioProperties.CircuitBreakerProperties(10, 50.0f, Duration.ofSeconds(10)));
  }

  @Test
  @DisplayName("resolvePreviewUrl with successful Deezer response returns preview URL")
  void resolvePreviewUrl_success_returnsPreviewUrl() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    DeezerAudioSourceResolver resolver =
        new DeezerAudioSourceResolver(createProperties(), builder, null, null);

    server
        .expect(once(), requestTo(BASE_URL + "/track/12345"))
        .andRespond(
            withSuccess(
                """
                {
                  "id": 12345,
                  "title": "Sample Song",
                  "preview": "https://cdns-preview-1.dzcdn.net/stream/sample.mp3"
                }
                """,
                MediaType.APPLICATION_JSON));

    Optional<String> url = resolver.resolvePreviewUrl(UUID.randomUUID(), "DEEZER", "12345");

    server.verify();
    assertThat(url).contains("https://cdns-preview-1.dzcdn.net/stream/sample.mp3");
  }

  @Test
  @DisplayName("resolvePreviewUrl with 404 does not retry and returns empty")
  void resolvePreviewUrl_notFound_doesNotRetryAndReturnsEmpty() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    DeezerAudioSourceResolver resolver =
        new DeezerAudioSourceResolver(createProperties(), builder, null, null);

    server
        .expect(once(), requestTo(BASE_URL + "/track/non-existent"))
        .andRespond(withRawStatus(404));

    Optional<String> url = resolver.resolvePreviewUrl(UUID.randomUUID(), "DEEZER", "non-existent");

    server.verify();
    assertThat(url).isEmpty();
  }

  @Test
  @DisplayName("resolvePreviewUrl with Deezer JSON error payload returns empty")
  void resolvePreviewUrl_deezerErrorPayload_returnsEmpty() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    DeezerAudioSourceResolver resolver =
        new DeezerAudioSourceResolver(createProperties(), builder, null, null);

    server
        .expect(once(), requestTo(BASE_URL + "/track/error-track"))
        .andRespond(
            withSuccess(
                """
                {
                  "error": {
                    "type": "DataException",
                    "message": "no data",
                    "code": 800
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    Optional<String> url = resolver.resolvePreviewUrl(UUID.randomUUID(), "DEEZER", "error-track");

    server.verify();
    assertThat(url).isEmpty();
  }

  @Test
  @DisplayName("resolvePreviewUrl with 5xx server error retries 3 times then returns empty")
  void resolvePreviewUrl_serverError_retriesThreeTimesThenReturnsEmpty() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    DeezerAudioSourceResolver resolver =
        new DeezerAudioSourceResolver(createProperties(), builder, null, null);

    server
        .expect(times(3), requestTo(BASE_URL + "/track/failing-track"))
        .andRespond(withServerError());

    Optional<String> url = resolver.resolvePreviewUrl(UUID.randomUUID(), "DEEZER", "failing-track");

    server.verify();
    assertThat(url).isEmpty();
  }
}
