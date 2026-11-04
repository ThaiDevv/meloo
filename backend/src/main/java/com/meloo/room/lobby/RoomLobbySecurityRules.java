package com.meloo.room.lobby;

import com.meloo.shared.api.ModuleSecurityRules;
import java.util.List;
import org.springframework.stereotype.Component;

/** Quy tắc bảo mật cho API REST của phòng chơi (/api/v1/rooms/**). */
@Component
public class RoomLobbySecurityRules implements ModuleSecurityRules {

  @Override
  public List<HttpRule> httpRules() {
    return List.of(HttpRule.authenticated(null, "/api/v1/rooms/**"));
  }
}
