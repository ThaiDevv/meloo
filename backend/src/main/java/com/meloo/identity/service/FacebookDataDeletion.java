package com.meloo.identity.service;

import com.meloo.generated.model.FacebookDataDeletionResponseDto;
import com.meloo.identity.config.FacebookOAuthProperties;
import com.meloo.identity.config.GoogleOAuthProperties;
import com.meloo.shared.api.ApiException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

@Service
public class FacebookDataDeletion {
  private final FacebookOAuthProperties properties;
  private final GoogleOAuthProperties oauth;
  private final ObjectMapper mapper;
  private final AccountDeletionService deletion;

  public FacebookDataDeletion(
      FacebookOAuthProperties properties,
      GoogleOAuthProperties oauth,
      ObjectMapper mapper,
      AccountDeletionService deletion) {
    this.properties = properties;
    this.oauth = oauth;
    this.mapper = mapper;
    this.deletion = deletion;
  }

  public FacebookDataDeletionResponseDto handle(String signedRequest) {
    String subject;
    try {
      if (properties.clientSecret() == null
          || properties.clientSecret().isBlank()
          || signedRequest == null
          || signedRequest.length() > 4096) throw new IllegalArgumentException();
      String[] parts = signedRequest.split("\\.", -1);
      if (parts.length != 2) throw new IllegalArgumentException();
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              properties.clientSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      if (!MessageDigest.isEqual(
          mac.doFinal(parts[1].getBytes(StandardCharsets.US_ASCII)),
          Base64.getUrlDecoder().decode(parts[0]))) throw new IllegalArgumentException();
      var payload = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
      subject = payload.path("user_id").asText("");
      if (!"HMAC-SHA256".equals(payload.path("algorithm").asText())
          || subject.isBlank()
          || subject.length() > 128) throw new IllegalArgumentException();
    } catch (Exception e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid signed request");
    }
    deletion.deleteFacebookAccount(subject);
    String code = "fb_del_" + UUID.randomUUID().toString().replace("-", "");
    URI url =
        UriComponentsBuilder.fromUri(oauth.spaOrigin())
            .replacePath("/privacy")
            .queryParam("deletion_code", code)
            .build()
            .toUri();
    return new FacebookDataDeletionResponseDto().url(url).confirmationCode(code);
  }
}
