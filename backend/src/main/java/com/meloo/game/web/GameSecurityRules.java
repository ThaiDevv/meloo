package com.meloo.game.web;

import com.meloo.shared.api.ModuleSecurityRules;
import java.util.List;
import org.springframework.stereotype.Component;

/** Quy tắc bảo mật cho module game: bảo vệ các API chơi đơn (/api/v1/solo/**). */
@Component
public class GameSecurityRules implements ModuleSecurityRules {

  @Override
  public List<HttpRule> httpRules() {
    return List.of(HttpRule.authenticated(null, "/api/v1/solo/**"));
  }
}
