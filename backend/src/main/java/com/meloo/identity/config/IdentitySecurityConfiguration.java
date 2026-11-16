package com.meloo.identity.config;

import com.meloo.identity.persistence.AppUserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration(proxyBeanMethods = false)
public class IdentitySecurityConfiguration {

  @Bean
  @Fallback
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  @Fallback
  JwtDecoder jwtDecoder(IdentityProperties properties, AppUserRepository users) {
    SecretKey key =
        new SecretKeySpec(properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    var decoder = NimbusJwtDecoder.withSecretKey(key).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefault(),
            token -> {
              try {
                var id = java.util.UUID.fromString(token.getSubject());
                if (users.existsByIdAndMergedIntoId(id, id)) {
                  return OAuth2TokenValidatorResult.failure(
                      new OAuth2Error("invalid_token", "Account deleted", null));
                }
                return OAuth2TokenValidatorResult.success();
              } catch (IllegalArgumentException | NullPointerException e) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
              }
            }));
    return decoder;
  }
}
