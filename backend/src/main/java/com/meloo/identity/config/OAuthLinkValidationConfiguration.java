package com.meloo.identity.config;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI generates @Size on the URI-typed redirectUri; validate its textual length. */
@Configuration(proxyBeanMethods = false)
public class OAuthLinkValidationConfiguration {
  @Bean
  ValidationConfigurationCustomizer oauthUriSizeValidation() {
    return configuration -> {
      if (configuration instanceof HibernateValidatorConfiguration hibernate) {
        var mapping = hibernate.createConstraintMapping();
        mapping
            .constraintDefinition(Size.class)
            .includeExistingValidators(true)
            .validatedBy(UriSizeValidator.class);
        hibernate.addMapping(mapping);
      }
    };
  }

  public static final class UriSizeValidator implements ConstraintValidator<Size, URI> {
    private int min;
    private int max;

    @Override
    public void initialize(Size constraint) {
      min = constraint.min();
      max = constraint.max();
    }

    @Override
    public boolean isValid(URI value, ConstraintValidatorContext context) {
      return value == null
          || (value.toString().length() >= min && value.toString().length() <= max);
    }
  }
}
