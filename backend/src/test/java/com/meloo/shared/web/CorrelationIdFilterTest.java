package com.meloo.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  void preservesValidCorrelationId() throws Exception {
    String correlationId = UUID.randomUUID().toString();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.HEADER_NAME, correlationId);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo(correlationId);
    assertThat(request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE))
        .isEqualTo(correlationId);
  }

  @Test
  void replacesUntrustedCorrelationId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.HEADER_NAME, "not-a-uuid\nforged-log");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    String generated = response.getHeader(CorrelationIdFilter.HEADER_NAME);
    assertThat(UUID.fromString(generated)).isNotNull();
    assertThat(generated).doesNotContain("forged-log");
  }
}
