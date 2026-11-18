package com.meloo.admin.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.meloo.catalog.api.AdminTrackView;
import com.meloo.generated.model.AdminAnalyticsDto;
import com.meloo.generated.model.AdminAuditLogDto;
import com.meloo.generated.model.AdminRatioDto;
import com.meloo.generated.model.AdminTrackDto;
import org.springframework.boot.jackson.JacksonMixin;

/** The contract requires these nullable fields even when the shared mapper omits nulls. */
@JacksonMixin({
  AdminTrackDto.class,
  AdminAuditLogDto.class,
  AdminTrackView.class,
  AdminAnalyticsDto.class,
  AdminRatioDto.class
})
@JsonInclude(JsonInclude.Include.ALWAYS)
abstract class AdminJsonFields {}
