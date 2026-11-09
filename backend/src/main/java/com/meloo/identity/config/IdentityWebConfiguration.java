package com.meloo.identity.config;

import com.meloo.generated.model.OAuthProviderSlugDto;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class IdentityWebConfiguration implements WebMvcConfigurer {
  @Override
  public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(
        String.class, OAuthProviderSlugDto.class, OAuthProviderSlugDto::fromValue);
  }
}
