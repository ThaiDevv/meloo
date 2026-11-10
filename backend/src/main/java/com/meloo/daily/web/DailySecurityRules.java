package com.meloo.daily.web;

import com.meloo.shared.api.ModuleSecurityRules;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DailySecurityRules implements ModuleSecurityRules {
  @Override
  public List<HttpRule> httpRules() {
    return List.of(HttpRule.authenticated(null, "/api/v1/daily/**", "/api/v1/leaderboards/daily"));
  }
}
