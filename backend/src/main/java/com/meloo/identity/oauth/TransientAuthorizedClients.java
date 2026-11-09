package com.meloo.identity.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/** Provider tokens are used only to fetch identity and are never persisted in an HTTP session. */
public final class TransientAuthorizedClients implements OAuth2AuthorizedClientRepository {
  @Override
  @Nullable public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
      String id, @Nullable Authentication principal, HttpServletRequest request) {
    return null;
  }

  @Override
  public void saveAuthorizedClient(
      OAuth2AuthorizedClient client,
      @Nullable Authentication principal,
      HttpServletRequest request,
      HttpServletResponse response) {}

  @Override
  public void removeAuthorizedClient(
      String id,
      @Nullable Authentication principal,
      HttpServletRequest request,
      HttpServletResponse response) {}
}
