package com.meloo.room.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.meloo.shared.api.ModuleSecurityRules.StompDestinationType;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class RoomSecurityRulesTest {

  @Test
  void exposesOnlyTheHandshakeAndDeclaresRoomAndPrivateQueueSecurity() {
    RoomSecurityRules rules = new RoomSecurityRules(mock(RoomStompChannelInterceptor.class));

    assertThat(rules.httpRules())
        .singleElement()
        .satisfies(
            rule -> {
              assertThat(rule.method()).isEqualTo(HttpMethod.GET);
              assertThat(rule.patterns()).containsExactly("/ws");
            });
    assertThat(rules.stompRules())
        .extracting("destinationType", "patterns", "access")
        .containsExactlyInAnyOrder(
            Tuple.tuple(
                StompDestinationType.SEND,
                List.of("/app/rooms/**"),
                com.meloo.shared.api.ModuleSecurityRules.Access.AUTHENTICATED),
            Tuple.tuple(
                StompDestinationType.SUBSCRIBE,
                List.of("/topic/rooms/**"),
                com.meloo.shared.api.ModuleSecurityRules.Access.AUTHENTICATED),
            Tuple.tuple(
                StompDestinationType.SUBSCRIBE,
                List.of("/user/queue/**"),
                com.meloo.shared.api.ModuleSecurityRules.Access.AUTHENTICATED));
  }
}
