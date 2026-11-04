package com.meloo.room.lobby;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RoomLobbyProperties.class)
public class RoomLobbyConfiguration {}
