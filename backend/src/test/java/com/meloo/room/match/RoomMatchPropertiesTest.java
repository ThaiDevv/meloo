package com.meloo.room.match;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

class RoomMatchPropertiesTest {
  @Test
  void loadsProductionDefaultsAndShortE2eProfileFromOwnedYaml() {
    try (var production =
        new SpringApplicationBuilder(RoomMatchConfiguration.class)
            .web(WebApplicationType.NONE)
            .properties("spring.config.location=classpath:config/room.yml")
            .run()) {
      RoomMatchProperties config = production.getBean(RoomMatchProperties.class);
      assertThat(config.prepareTimeout()).isEqualTo(Duration.ofSeconds(5));
      assertThat(config.roundDuration()).isEqualTo(Duration.ofSeconds(45));
      assertThat(config.timeoutBuffer()).isEqualTo(Duration.ofMillis(250));
    }
    try (var e2e =
        new SpringApplicationBuilder(RoomMatchConfiguration.class)
            .web(WebApplicationType.NONE)
            .profiles("e2e")
            .properties("spring.config.location=classpath:config/room.yml")
            .run()) {
      RoomMatchProperties config = e2e.getBean(RoomMatchProperties.class);
      assertThat(config.prepareTimeout()).isEqualTo(Duration.ofMillis(200));
      assertThat(config.roundDuration()).isEqualTo(Duration.ofSeconds(2));
      assertThat(e2e.getEnvironment().getProperty("meloo.room.lobby.disconnect-timeout"))
          .isEqualTo("1s");
    }
  }

  @Test
  void rejectsInvalidDurationsRatherThanSchedulingBrokenTimers() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new RoomMatchProperties(
                    Duration.ZERO,
                    Duration.ofSeconds(45),
                    Duration.ofMillis(250),
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(10)));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new RoomMatchProperties(
                    Duration.ofNanos(1),
                    Duration.ofSeconds(45),
                    Duration.ofMillis(250),
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(10)));
  }
}
