package com.meloo.daily.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableConfigurationProperties(DailyProperties.class)
public class DailyConfiguration {
  @Bean
  ThreadPoolTaskScheduler dailyExpiryScheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("daily-expiry-");
    return scheduler;
  }
}
