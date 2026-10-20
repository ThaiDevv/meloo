package com.meloo.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meloo.shared.api.ModuleSecurityRules.Access;
import com.meloo.shared.api.ModuleSecurityRules.HttpRule;
import com.meloo.shared.api.ModuleSecurityRules.StompDestinationType;
import com.meloo.shared.api.ModuleSecurityRules.StompRule;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class ModuleSecurityRulesTest {

  @Test
  void factoriesCreateHttpAndStompDeclarations() {
    HttpRule http = HttpRule.authenticated(HttpMethod.GET, "/api/v1/me");
    StompRule stomp =
        StompRule.hasAuthority(StompDestinationType.SUBSCRIBE, "ROOM_MEMBER", "/topic/rooms/*");

    assertThat(http.access()).isEqualTo(Access.AUTHENTICATED);
    assertThat(http.patterns()).containsExactly("/api/v1/me");
    assertThat(stomp.access()).isEqualTo(Access.AUTHORITY);
    assertThat(stomp.authority()).isEqualTo("ROOM_MEMBER");
  }

  @Test
  void authorityRuleRequiresNonBlankAuthority() {
    assertThatThrownBy(() -> HttpRule.hasAuthority(HttpMethod.POST, " ", "/api/v1/admin/**"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Authority");
  }

  @Test
  void ruleRequiresAtLeastOnePattern() {
    assertThatThrownBy(() -> HttpRule.permitAll(HttpMethod.GET))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pattern");
  }
}
