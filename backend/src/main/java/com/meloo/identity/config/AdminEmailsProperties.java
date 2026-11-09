package com.meloo.identity.config;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("meloo.admin")
public record AdminEmailsProperties(Set<String> emails) {
  public AdminEmailsProperties {
    emails =
        emails == null
            ? Set.of()
            : emails.stream()
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
  }
}
