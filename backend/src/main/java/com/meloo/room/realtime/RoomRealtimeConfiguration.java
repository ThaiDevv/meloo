package com.meloo.room.realtime;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(RoomRealtimeProperties.class)
public class RoomRealtimeConfiguration implements WebSocketMessageBrokerConfigurer {

  private final RoomRealtimeProperties properties;
  private final ThreadPoolTaskScheduler scheduler;

  public RoomRealtimeConfiguration(
      RoomRealtimeProperties properties, ThreadPoolTaskScheduler roomRealtimeScheduler) {
    this.properties = properties;
    this.scheduler = roomRealtimeScheduler;
  }

  @Bean(name = {"taskScheduler", "roomRealtimeScheduler"})
  static ThreadPoolTaskScheduler roomRealtimeScheduler(RoomRealtimeProperties properties) {
    ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
    taskScheduler.setPoolSize(properties.schedulerPoolSize());
    taskScheduler.setThreadNamePrefix("meloo-room-timer-");
    taskScheduler.setRemoveOnCancelPolicy(true);
    taskScheduler.setWaitForTasksToCompleteOnShutdown(false);
    taskScheduler.setThreadFactory(virtualThreadFactory());
    return taskScheduler;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    long heartbeatMillis = properties.heartbeatInterval().toMillis();
    registry
        .enableSimpleBroker("/topic", "/queue")
        .setTaskScheduler(scheduler)
        .setHeartbeatValue(new long[] {heartbeatMillis, heartbeatMillis});
    registry.setApplicationDestinationPrefixes("/app");
    registry.setUserDestinationPrefix("/user");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws");
  }

  private static ThreadFactory virtualThreadFactory() {
    AtomicInteger sequence = new AtomicInteger();
    return task ->
        Thread.ofVirtual().name("meloo-room-timer-" + sequence.incrementAndGet()).unstarted(task);
  }
}
