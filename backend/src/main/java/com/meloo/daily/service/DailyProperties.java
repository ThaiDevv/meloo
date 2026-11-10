package com.meloo.daily.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("meloo.daily")
public record DailyProperties(
    @Min(1) @Max(1000) int popularPoolSize,
    @Min(1) @Max(10000) int actionsPerMinute,
    @Min(1000) long expiryScanMs) {}
