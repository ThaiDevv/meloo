package com.meloo.progression.web;

import com.meloo.shared.api.ModuleSecurityRules;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
public class ProgressionSecurityRules implements ModuleSecurityRules {
  @Override
  public List<HttpRule> httpRules() {
    return List.of(
        HttpRule.authenticated(
            HttpMethod.GET,
            "/api/v1/me/profile",
            "/api/v1/me/matches/**",
            "/api/v1/me/stats",
            "/api/v1/leaderboards/all-time"),
        HttpRule.authenticated(HttpMethod.PATCH, "/api/v1/me/profile"));
  }
}
