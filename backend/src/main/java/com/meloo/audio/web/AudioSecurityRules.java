package com.meloo.audio.web;

import com.meloo.shared.api.ModuleSecurityRules;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * Public security declaration for the audio streaming endpoint.
 *
 * <p>GET /api/v1/audio/* is publicly accessible without an Authorization header because HTML5
 * {@code <audio>} elements cannot send custom headers. Authentication is handled by verifying the
 * HMAC token in the path.
 */
@Component
public class AudioSecurityRules implements ModuleSecurityRules {

  @Override
  public List<HttpRule> httpRules() {
    return List.of(HttpRule.permitAll(HttpMethod.GET, "/api/v1/audio/*"));
  }
}
