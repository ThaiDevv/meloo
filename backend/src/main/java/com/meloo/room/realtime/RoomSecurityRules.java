package com.meloo.room.realtime;

import com.meloo.shared.api.ModuleSecurityRules;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.stereotype.Component;

@Component
public class RoomSecurityRules implements ModuleSecurityRules {

  private final RoomStompChannelInterceptor interceptor;

  public RoomSecurityRules(RoomStompChannelInterceptor interceptor) {
    this.interceptor = interceptor;
  }

  @Override
  public List<HttpRule> httpRules() {
    return List.of(HttpRule.permitAll(HttpMethod.GET, "/ws"));
  }

  @Override
  public List<StompRule> stompRules() {
    return List.of(
        StompRule.authenticated(StompDestinationType.SEND, "/app/rooms/**"),
        StompRule.authenticated(StompDestinationType.SUBSCRIBE, "/topic/rooms/**"),
        StompRule.authenticated(StompDestinationType.SUBSCRIBE, "/user/queue/**"));
  }

  @Override
  public void configureStompInboundChannel(ChannelRegistration registration) {
    registration.interceptors(interceptor);
  }
}
