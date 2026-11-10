package com.meloo.admin.web;

import com.meloo.identity.api.UserAccounts;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component("adminAccess")
public class AdminAccess {
  private final UserAccounts accounts;

  public AdminAccess(UserAccounts accounts) {
    this.accounts = accounts;
  }

  public boolean allowed() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken token)
        || !"ADMIN".equals(token.getToken().getClaimAsString("role"))) {
      return false;
    }
    try {
      var user = accounts.get(UUID.fromString(token.getName()));
      return "ADMIN".equals(user.role()) && "ACTIVE".equals(user.status());
    } catch (com.meloo.shared.api.ApiException | IllegalArgumentException ex) {
      return false;
    }
  }

  public UUID actor() {
    if (!allowed()) {
      throw new AccessDeniedException("Administrator access required");
    }
    return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
  }
}
