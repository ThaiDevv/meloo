package com.meloo.progression.achievement;

import com.meloo.shared.api.ModuleSecurityRules;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
public class AchievementSecurityRules implements ModuleSecurityRules {
  @Override
  public List<HttpRule> httpRules() {
    return List.of(HttpRule.authenticated(HttpMethod.GET, "/api/v1/me/achievements"));
  }
}
