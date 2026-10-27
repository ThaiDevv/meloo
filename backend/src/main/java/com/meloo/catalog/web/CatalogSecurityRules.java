package com.meloo.catalog.web;

import com.meloo.shared.api.ModuleSecurityRules;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
public class CatalogSecurityRules implements ModuleSecurityRules {

  @Override
  public List<HttpRule> httpRules() {
    return List.of(
        HttpRule.authenticated(HttpMethod.GET, "/api/v1/tracks/search"),
        HttpRule.authenticated(HttpMethod.GET, "/api/v1/playlists"),
        HttpRule.authenticated(HttpMethod.GET, "/api/v1/filters"));
  }
}
