package com.meloo.identity.config;

import com.meloo.generated.model.FacebookDataDeletionRequestDto;
import java.io.IOException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.stereotype.Component;

/** The generated interface uses one request DTO for both JSON and Meta's form POST. */
@Component
public class FacebookFormConverter
    extends AbstractHttpMessageConverter<FacebookDataDeletionRequestDto> {
  public FacebookFormConverter() {
    super(MediaType.APPLICATION_FORM_URLENCODED);
  }

  @Override
  protected boolean supports(Class<?> type) {
    return type == FacebookDataDeletionRequestDto.class;
  }

  @Override
  protected FacebookDataDeletionRequestDto readInternal(
      Class<? extends FacebookDataDeletionRequestDto> type, HttpInputMessage input)
      throws IOException {
    var fields = new FormHttpMessageConverter().read(null, input);
    return new FacebookDataDeletionRequestDto().signedRequest(fields.getFirst("signed_request"));
  }

  @Override
  protected void writeInternal(FacebookDataDeletionRequestDto value, HttpOutputMessage output) {
    throw new UnsupportedOperationException("Request-only converter");
  }
}
