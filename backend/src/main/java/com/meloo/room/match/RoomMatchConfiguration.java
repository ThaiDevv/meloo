package com.meloo.room.match;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RoomMatchProperties.class)
public class RoomMatchConfiguration {
  @Bean(destroyMethod = "close")
  ExecutorService roomMatchIoExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }
}
